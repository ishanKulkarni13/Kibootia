package com.kibootia.backend.access.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.access.domain.DefaultDecisionPoint;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.platform.authorization.AccessFilter;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The control of milestone 8 (section 8): converting stored permissions of contract version 1 into grants changes no
 * decision. For every set of stored names a role can hold (every subset of the declared names, their {@code -any}
 * variants and a name no module declares any more), every action and every kind of record, the decision of contract
 * version 1 and the decision after {@link BuiltInRoles#legacyGrant} are the same, and so is the list filter.
 */
class LegacyGrantEquivalenceTest {

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-000000000002");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final String WIDGET = "widgets.widget";

	/** The actions that had an {@code -any} sibling in version 1: the actions on a record with an owner. */
	static final List<String> OWNABLE = List.of("widgets.widgets.view", "widgets.widgets.update");

	/** An administrative action, which had no sibling and meant every record. */
	static final String RESTORE = "widgets.widgets.restore";

	/** The same action on a record, but of a type without an owner: in version 1 it had no sibling either. */
	static final String AUDIT = "widgets.widgets.audit";

	static final PermissionRegistry REGISTRY = new PermissionRegistry(List.of(
			Permission.onRecord("widgets.widgets.view", WIDGET, "View widgets").forEveryUser(Reach.owner()),
			Permission.onRecord("widgets.widgets.update", WIDGET, "Change widgets").forEveryUser(Reach.owner()),
			Permission.onCollection(RESTORE, "Restore widgets"),
			Permission.onRecord(AUDIT, "widgets.ledger", "Audit ledgers")),
			List.of(ResourceType.of(WIDGET, "A widget", ResourceRef.OWNER), ResourceType.of("widgets.ledger", "A ledger")));

	/** Every name a role of version 1 could have stored, including one whose module is gone. */
	static final List<String> STORED = List.of("widgets.widgets.view", "widgets.widgets.view-any", "widgets.widgets.update",
			"widgets.widgets.update-any", RESTORE, AUDIT, "gone.things.read");

	static final List<String> ACTIONS = List.of("widgets.widgets.view", "widgets.widgets.update", RESTORE, AUDIT);

	// --- contract version 1, as DefaultDecisionPoint decided it before milestone 8 ---------------------------------------

	enum Scope {

		ALL, OWN, NONE

	}

	static Scope legacyScope(Set<String> held, String action) {
		if (held.contains(action + "-any")) {
			return Scope.ALL;
		}
		if (held.contains(action)) {
			return OWNABLE.contains(action) ? Scope.OWN : Scope.ALL;
		}
		return Scope.NONE;
	}

	static boolean legacyCan(Set<String> held, String action, ResourceRef record) {
		Scope scope = legacyScope(held, action);
		if (scope == Scope.NONE) {
			return false;
		}
		if (record == null) {
			return true;
		}
		if (!TENANT.equals(record.tenantId())) {
			return false;
		}
		return scope == Scope.ALL || record.hasRelation(ResourceRef.OWNER, ALICE);
	}

	// --- the grant model, after conversion --------------------------------------------------------------------------

	static Set<Grant> converted(Set<String> held) {
		Set<Grant> grants = new LinkedHashSet<>();
		for (String name : held) {
			BuiltInRoles.legacyGrant(REGISTRY, name).ifPresent(grants::add);
		}
		return grants;
	}

	static List<Set<String>> everySubset() {
		List<Set<String>> subsets = new ArrayList<>();
		for (int mask = 0; mask < (1 << STORED.size()); mask++) {
			Set<String> subset = new LinkedHashSet<>();
			for (int bit = 0; bit < STORED.size(); bit++) {
				if ((mask & (1 << bit)) != 0) {
					subset.add(STORED.get(bit));
				}
			}
			subsets.add(subset);
		}
		return subsets;
	}

	@Test
	@DisplayName("every decision and every list filter is the same before and after converting the stored permissions")
	void decisionsAreEquivalent() {
		Principal.User alice = new Principal.User(ALICE, TENANT, true);
		List<Optional<ResourceRef>> records = new ArrayList<>();
		records.add(Optional.empty());
		for (String type : List.of(WIDGET, "widgets.ledger")) {
			records.add(Optional.of(ResourceRef.of(type, UUID.randomUUID(), TENANT).ownedBy(ALICE)));
			records.add(Optional.of(ResourceRef.of(type, UUID.randomUUID(), TENANT).ownedBy(BOB)));
			records.add(Optional.of(ResourceRef.of(type, UUID.randomUUID(), OTHER_TENANT).ownedBy(ALICE)));
		}
		int compared = 0;
		for (Set<String> held : everySubset()) {
			DefaultDecisionPoint point = new DefaultDecisionPoint(REGISTRY, (user) -> converted(held), List.of());
			for (String action : ACTIONS) {
				for (Optional<ResourceRef> record : records) {
					boolean before = legacyCan(held, action, record.orElse(null));
					boolean after = point.decide(alice, action, record.orElse(null)).allowed();
					assertThat(after).as("%s on %s holding %s", action, record, held).isEqualTo(before);
					compared++;
				}
				AccessFilter filter = point.filter(alice, action);
				AccessFilter expected = switch (legacyScope(held, action)) {
					case ALL -> AccessFilter.EVERYTHING;
					case OWN -> AccessFilter.relatedTo(ResourceRef.OWNER, ALICE);
					case NONE -> AccessFilter.NOTHING;
				};
				assertThat(filter).as("filter of %s holding %s", action, held).isEqualTo(expected);
			}
		}
		assertThat(compared).isEqualTo(128 * ACTIONS.size() * records.size());
	}

	@Test
	@DisplayName("the conversion rule: x-any reaches all, an ownable x reaches its owner, others reach all, unknown is left")
	void conversionRule() {
		assertThat(BuiltInRoles.legacyGrant(REGISTRY, "widgets.widgets.update-any"))
			.contains(Grant.allow("widgets.widgets.update", Reach.ALL));
		assertThat(BuiltInRoles.legacyGrant(REGISTRY, "widgets.widgets.update"))
			.contains(Grant.allow("widgets.widgets.update", Reach.owner()));
		assertThat(BuiltInRoles.legacyGrant(REGISTRY, RESTORE)).contains(Grant.allow(RESTORE, Reach.ALL));
		assertThat(BuiltInRoles.legacyGrant(REGISTRY, AUDIT)).contains(Grant.allow(AUDIT, Reach.ALL));
		assertThat(BuiltInRoles.legacyGrant(REGISTRY, "gone.things.read")).isEmpty();
		assertThat(BuiltInRoles.legacyGrant(REGISTRY, "gone.things.read-any")).isEmpty();
	}

}
