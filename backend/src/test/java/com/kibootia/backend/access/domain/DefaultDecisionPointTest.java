package com.kibootia.backend.access.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.authorization.AccessFilter;
import com.kibootia.backend.platform.authorization.Decision;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.Reason;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceRule;
import com.kibootia.backend.platform.authorization.ResourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The combination rule of the decision point (contract version 2, ADR 0055) on the grant model (ADR 0054). Every case of
 * earlier versions is kept with its expected outcome: a grant reaching every record is what holding {@code x-any} was,
 * a grant reaching the holder's own records is what holding {@code x} was when {@code x-any} existed.
 */
class DefaultDecisionPointTest {

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-000000000002");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final String WIDGET = "widgets.widget";

	static final String UPDATE = "widgets.widgets.update";

	static final String RESTORE = "widgets.widgets.restore";

	static final PermissionRegistry REGISTRY = new PermissionRegistry(List.of(
			Permission.onRecord(UPDATE, WIDGET, "Change widgets").forEveryUser(Reach.owner()),
			Permission.onCollection(RESTORE, "Restore deleted widgets").asSensitive()),
			List.of(ResourceType.of(WIDGET, "A widget", ResourceRef.OWNER)));

	static final Grant OWN_UPDATES = Grant.allow(UPDATE, Reach.owner());

	static final Grant ALL_UPDATES = Grant.allow(UPDATE, Reach.ALL);

	static final Grant RESTORES = Grant.allow(RESTORE, Reach.ALL);

	static ResourceRef widget(UUID owner, UUID tenant) {
		return ResourceRef.of(WIDGET, UUID.randomUUID(), tenant).ownedBy(owner);
	}

	static Principal alice() {
		return new Principal.User(ALICE, TENANT, true);
	}

	static Principal system() {
		return new Principal.System(TENANT, "test: a job");
	}

	static DefaultDecisionPoint pointFor(Set<Grant> held, ResourceRule... rules) {
		return new DefaultDecisionPoint(REGISTRY, (user) -> held, List.of(rules));
	}

	/** A rule that objects to every action on widgets, or to none. */
	static ResourceRule rule(String name, boolean objects, List<String> consulted) {
		return new ResourceRule() {

			@Override
			public String name() {
				return name;
			}

			@Override
			public boolean appliesTo(String resourceType, String action) {
				return WIDGET.equals(resourceType);
			}

			@Override
			public Optional<String> objection(Principal principal, String action, ResourceRef resource) {
				consulted.add(name);
				return objects ? Optional.of(name + " objects") : Optional.empty();
			}

		};
	}

	@Nested
	@DisplayName("the registry of declared actions and resource types")
	class Registry {

		@Test
		@DisplayName("lists actions, the user role's grants, the administrator's grants, and the sensitive ones")
		void listsDeclarations() {
			assertThat(REGISTRY.all()).extracting(Permission::name).containsExactly(UPDATE, RESTORE);
			assertThat(REGISTRY.userRoleGrants()).containsExactly(OWN_UPDATES);
			assertThat(REGISTRY.administratorGrants()).containsExactlyInAnyOrder(ALL_UPDATES, RESTORES);
			assertThat(REGISTRY.isSensitive(RESTORE)).isTrue();
			assertThat(REGISTRY.isSensitive(UPDATE)).isFalse();
		}

		@Test
		@DisplayName("offers 'all' for every action and the type's relations for an action on a record")
		void reaches() {
			assertThat(REGISTRY.reachesOf(UPDATE)).containsExactly(Reach.ALL, Reach.owner());
			assertThat(REGISTRY.reachesOf(RESTORE)).containsExactly(Reach.ALL);
			assertThat(REGISTRY.isOnRecord(UPDATE)).isTrue();
			assertThat(REGISTRY.isOnRecord(RESTORE)).isFalse();
		}

		@Test
		@DisplayName("a valid grant has a declared action, a reach its type offers, and denies only with reach 'all'")
		void validGrants() {
			assertThat(REGISTRY.isValid(OWN_UPDATES)).isTrue();
			assertThat(REGISTRY.isValid(Grant.deny(UPDATE))).isTrue();
			assertThat(REGISTRY.isValid(Grant.allow(RESTORE, Reach.owner()))).as("a collection action has no owner").isFalse();
			assertThat(REGISTRY.isValid(Grant.allow(UPDATE, Reach.relation("reviewer")))).isFalse();
			assertThat(REGISTRY.isValid(new Grant(UPDATE, Reach.owner(), Grant.Effect.DENY))).isFalse();
			assertThat(REGISTRY.isValid(Grant.allow("widgets.widgets.typo", Reach.ALL))).isFalse();
		}

		@Test
		@DisplayName("refuses a name that is not <module>.<resource>.<action>")
		void refusesBadNames() {
			for (String bad : List.of("update", "widgets.update", "Widgets.widgets.update", "widgets.widgets.Update",
					"widgets.widgets.update.again", "widgets..update", "widgets.widgets.update_any")) {
				assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.onCollection(bad, "x"))))
					.as(bad)
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining(bad);
			}
		}

		@Test
		@DisplayName("refuses a permission declared twice")
		void refusesDuplicates() {
			assertThatThrownBy(() -> new PermissionRegistry(
					List.of(Permission.onCollection(RESTORE, "a"), Permission.onCollection(RESTORE, "b"))))
				.hasMessageContaining(RESTORE);
		}

		@Test
		@DisplayName("refuses an action on a resource type nobody declares, and a user-role reach the type lacks")
		void refusesUndeclaredTypesAndReaches() {
			assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.onRecord(UPDATE, "widgets.ghost", "x"))))
				.hasMessageContaining("widgets.ghost");
			assertThatThrownBy(() -> new PermissionRegistry(
					List.of(Permission.onRecord(UPDATE, WIDGET, "x").forEveryUser(Reach.relation("reviewer"))),
					List.of(ResourceType.of(WIDGET, "A widget", ResourceRef.OWNER))))
				.hasMessageContaining("relation:reviewer");
			assertThatThrownBy(() -> Permission.onCollection(RESTORE, "x").forEveryUser(Reach.owner()))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	@DisplayName("the access filter of an action")
	class Filters {

		@Test
		@DisplayName("is nothing without a grant")
		void none() {
			assertThat(pointFor(Set.of()).filter(alice(), UPDATE)).isEqualTo(AccessFilter.NOTHING);
			assertThat(pointFor(Set.of()).filter(alice(), RESTORE)).isEqualTo(AccessFilter.NOTHING);
		}

		@Test
		@DisplayName("is the caller's own records with a grant reaching their own")
		void own() {
			AccessFilter filter = pointFor(Set.of(OWN_UPDATES)).filter(alice(), UPDATE);
			assertThat(filter).isEqualTo(AccessFilter.relatedTo(ResourceRef.OWNER, ALICE));
			assertThat(filter.ownerOnly()).contains(ALICE);
		}

		@Test
		@DisplayName("is everything with a grant reaching every record, also beside a narrower one")
		void all() {
			assertThat(pointFor(Set.of(OWN_UPDATES, ALL_UPDATES)).filter(alice(), UPDATE)).isEqualTo(AccessFilter.EVERYTHING);
			assertThat(pointFor(Set.of(ALL_UPDATES)).filter(alice(), UPDATE)).isEqualTo(AccessFilter.EVERYTHING);
			assertThat(AccessFilter.EVERYTHING.ownerOnly()).isEmpty();
		}

		@Test
		@DisplayName("is everything for an action on the collection")
		void collectionAction() {
			assertThat(pointFor(Set.of(RESTORES)).filter(alice(), RESTORE)).isEqualTo(AccessFilter.EVERYTHING);
		}

		@Test
		@DisplayName("a grant of another action grants nothing")
		void otherActionGrantsNothing() {
			assertThat(pointFor(Set.of(ALL_UPDATES)).filter(alice(), RESTORE)).isEqualTo(AccessFilter.NOTHING);
		}

		@Test
		@DisplayName("is everything for the system")
		void system() {
			assertThat(pointFor(Set.of()).filter(DefaultDecisionPointTest.system(), RESTORE))
				.isEqualTo(AccessFilter.EVERYTHING);
		}

		@Test
		@DisplayName("is an error for a permission nobody declared")
		void unknownPermission() {
			assertThatThrownBy(() -> pointFor(Set.of()).filter(alice(), "widgets.widgets.typo"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("widgets.widgets.typo");
		}

		@Test
		@DisplayName("a store that understands only ownership refuses a filter it does not know, rather than ignore it")
		void unknownShapeIsRefused() {
			AccessFilter twoOwners = new AccessFilter.AnyOf(List.of(new AccessFilter.RelatedTo(ResourceRef.OWNER, ALICE),
					new AccessFilter.RelatedTo(ResourceRef.OWNER, BOB)));
			assertThatThrownBy(twoOwners::ownerOnly).isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(AccessFilter.relatedTo("reviewer", ALICE)::ownerOnly)
				.isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(AccessFilter.NOTHING::ownerOnly).isInstanceOf(IllegalStateException.class);
		}

		@Test
		@DisplayName("ignores a stored grant that no longer fits the declarations")
		void ignoresInvalidGrants() {
			Set<Grant> stale = Set.of(Grant.allow(RESTORE, Reach.owner()), Grant.allow(UPDATE, Reach.relation("reviewer")),
					Grant.allow("gone.things.read", Reach.ALL));
			assertThat(pointFor(stale).filter(alice(), RESTORE)).isEqualTo(AccessFilter.NOTHING);
			assertThat(pointFor(stale).filter(alice(), UPDATE)).isEqualTo(AccessFilter.NOTHING);
		}

	}

	@Nested
	@DisplayName("deny grants (ADR 0054, rule 5)")
	class Denies {

		@Test
		@DisplayName("a deny decides over every allow, from any role, for a record, the collection and the list")
		void denyWins() {
			DefaultDecisionPoint point = pointFor(Set.of(ALL_UPDATES, OWN_UPDATES, Grant.deny(UPDATE)));
			Decision decision = point.decide(alice(), UPDATE, widget(ALICE, TENANT));
			assertThat(decision).isEqualTo(Decision.of(Reason.DENIED_BY_GRANT, "!" + UPDATE + "/all"));
			assertThat(point.decide(alice(), UPDATE, null).reason()).isEqualTo(Reason.DENIED_BY_GRANT);
			assertThat(point.filter(alice(), UPDATE)).isEqualTo(AccessFilter.NOTHING);
		}

		@Test
		@DisplayName("a deny of one action leaves the others alone")
		void denyIsPerAction() {
			assertThat(pointFor(Set.of(RESTORES, Grant.deny(UPDATE))).decide(alice(), RESTORE, null).allowed()).isTrue();
		}

		@Test
		@DisplayName("a deny that does not reach every record is not valid and is ignored, never half-applied")
		void partialDenyIsIgnored() {
			Grant ownDeny = new Grant(UPDATE, Reach.owner(), Grant.Effect.DENY);
			assertThat(pointFor(Set.of(ALL_UPDATES, ownDeny)).decide(alice(), UPDATE, widget(ALICE, TENANT)).allowed())
				.isTrue();
		}

		@Test
		@DisplayName("does not bind the system")
		void systemIsNotDenied() {
			assertThat(pointFor(Set.of(Grant.deny(RESTORE))).decide(DefaultDecisionPointTest.system(), RESTORE, null)
				.reason()).isEqualTo(Reason.SYSTEM);
		}

	}

	@Nested
	@DisplayName("a caller who has not verified their email address")
	class Unverified {

		static final String BEFORE = "widgets.widgets.read-own";

		final PermissionRegistry registry = new PermissionRegistry(List.of(
				Permission.onRecord(UPDATE, WIDGET, "Change widgets").forEveryUser(Reach.owner()),
				Permission.onRecord(BEFORE, WIDGET, "Read own widgets").forEveryUser(Reach.owner()).beforeVerification()),
				List.of(ResourceType.of(WIDGET, "A widget", ResourceRef.OWNER)));

		Principal unverified() {
			return new Principal.User(ALICE, TENANT, false);
		}

		@Test
		@DisplayName("holds nothing, even what the user role grants")
		void holdsNothing() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry,
					(user) -> Set.of(OWN_UPDATES, ALL_UPDATES, Grant.allow(BEFORE, Reach.owner())), List.of());
			assertThat(point.filter(unverified(), UPDATE)).isEqualTo(AccessFilter.NOTHING);
			Decision decision = point.decide(unverified(), UPDATE, widget(ALICE, TENANT));
			assertThat(decision.allowed()).isFalse();
			assertThat(decision.reason()).isEqualTo(Reason.EMAIL_NOT_VERIFIED);
		}

		@Test
		@DisplayName("holds an action marked as allowed before verification")
		void holdsMarkedPermission() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry,
					(user) -> Set.of(Grant.allow(BEFORE, Reach.owner())), List.of());
			assertThat(point.filter(unverified(), BEFORE)).isEqualTo(AccessFilter.relatedTo(ResourceRef.OWNER, ALICE));
		}

		@Test
		@DisplayName("is still denied a marked action it was never granted")
		void markedPermissionStillNeedsGranting() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (user) -> Set.of(), List.of());
			assertThat(point.filter(unverified(), BEFORE)).isEqualTo(AccessFilter.NOTHING);
		}

	}

	@Nested
	@DisplayName("a decision about one record")
	class Records {

		@Test
		@DisplayName("allows an owner with a grant reaching their own and denies someone else's record as out of reach")
		void ownership() {
			DefaultDecisionPoint point = pointFor(Set.of(OWN_UPDATES));
			assertThat(point.decide(alice(), UPDATE, widget(ALICE, TENANT)))
				.isEqualTo(Decision.of(Reason.GRANTED, OWN_UPDATES.describe()));
			assertThat(point.decide(alice(), UPDATE, widget(BOB, TENANT)))
				.isEqualTo(Decision.of(Reason.OUT_OF_REACH, OWN_UPDATES.describe()));
		}

		@Test
		@DisplayName("denies a grant reaching the holder's own on a record that has no owner")
		void recordWithoutOwner() {
			ResourceRef ownerless = ResourceRef.of(WIDGET, UUID.randomUUID(), TENANT);
			assertThat(pointFor(Set.of(OWN_UPDATES)).decide(alice(), UPDATE, ownerless).reason())
				.isEqualTo(Reason.OUT_OF_REACH);
		}

		@Test
		@DisplayName("allows any record in the tenant with a grant reaching all, and names the widest grant that reached")
		void anyRecord() {
			assertThat(pointFor(Set.of(OWN_UPDATES, ALL_UPDATES)).decide(alice(), UPDATE, widget(BOB, TENANT)))
				.isEqualTo(Decision.of(Reason.GRANTED, "widgets.widgets.update/all"));
		}

		@Test
		@DisplayName("denies without a grant, even for the owner")
		void withoutGrant() {
			assertThat(pointFor(Set.of()).decide(alice(), UPDATE, widget(ALICE, TENANT)))
				.isEqualTo(Decision.of(Reason.NO_GRANT));
		}

		@Test
		@DisplayName("denies a record of another tenant whatever the reach")
		void otherTenant() {
			assertThat(pointFor(Set.of(ALL_UPDATES)).decide(alice(), UPDATE, widget(ALICE, OTHER_TENANT)).reason())
				.isEqualTo(Reason.OTHER_TENANT);
			assertThat(pointFor(Set.of(OWN_UPDATES)).decide(alice(), UPDATE, widget(ALICE, OTHER_TENANT)).reason())
				.isEqualTo(Reason.OTHER_TENANT);
		}

		@Test
		@DisplayName("lets the system act on a record of its own tenant")
		void system() {
			assertThat(pointFor(Set.of()).decide(DefaultDecisionPointTest.system(), RESTORE, widget(BOB, TENANT)))
				.isEqualTo(Decision.of(Reason.SYSTEM, "system"));
		}

		@Test
		@DisplayName("decides an action on the collection from grants alone: any reach allows")
		void collectionAction() {
			assertThat(pointFor(Set.of(OWN_UPDATES)).decide(alice(), UPDATE, null))
				.isEqualTo(Decision.of(Reason.GRANTED, OWN_UPDATES.describe()));
			assertThat(pointFor(Set.of()).decide(alice(), RESTORE, null)).isEqualTo(Decision.of(Reason.NO_GRANT));
		}

		@Test
		@DisplayName("is an error for a permission nobody declared, even for the system")
		void unknownPermission() {
			assertThatThrownBy(() -> pointFor(Set.of()).decide(DefaultDecisionPointTest.system(), "widgets.widgets.typo", null))
				.isInstanceOf(IllegalStateException.class);
		}

	}

	@Nested
	@DisplayName("module rules, which can only narrow (finding S3)")
	class Rules {

		@Test
		@DisplayName("a rule cannot widen: someone else's record stays out of reach, and the rule is not even asked")
		void ruleCannotWiden() {
			List<String> consulted = new ArrayList<>();
			assertThat(pointFor(Set.of(OWN_UPDATES), rule("allows-everything", false, consulted))
				.decide(alice(), UPDATE, widget(BOB, TENANT)).reason()).isEqualTo(Reason.OUT_OF_REACH);
			assertThat(pointFor(Set.of(), rule("allows-everything", false, consulted))
				.decide(alice(), UPDATE, widget(ALICE, TENANT)).reason()).isEqualTo(Reason.NO_GRANT);
			assertThat(consulted).as("a rule is never consulted where grants refused").isEmpty();
		}

		@Test
		@DisplayName("a rule narrows what grants allow, and the decision names it")
		void ruleNarrows() {
			List<String> consulted = new ArrayList<>();
			assertThat(pointFor(Set.of(ALL_UPDATES), rule("locked-widgets", true, consulted))
				.decide(alice(), UPDATE, widget(BOB, TENANT))).isEqualTo(Decision.of(Reason.REFUSED_BY_RULE, "locked-widgets"));
		}

		@Test
		@DisplayName("every applicable rule is consulted until one objects, and any objection refuses")
		void allRulesApply() {
			List<String> consulted = new ArrayList<>();
			assertThat(pointFor(Set.of(OWN_UPDATES), rule("first", false, consulted), rule("second", true, consulted))
				.decide(alice(), UPDATE, widget(ALICE, TENANT))).isEqualTo(Decision.of(Reason.REFUSED_BY_RULE, "second"));
			assertThat(consulted).containsExactly("first", "second");

			List<String> both = new ArrayList<>();
			assertThat(pointFor(Set.of(OWN_UPDATES), rule("first", false, both), rule("second", false, both))
				.decide(alice(), UPDATE, widget(ALICE, TENANT)).allowed()).isTrue();
			assertThat(both).containsExactly("first", "second");
		}

		@Test
		@DisplayName("a rule that throws refuses, as an evaluation error, and later rules do not override it")
		void failingRuleRefuses() {
			ResourceRule broken = new ResourceRule() {

				@Override
				public String name() {
					return "broken";
				}

				@Override
				public boolean appliesTo(String resourceType, String action) {
					return true;
				}

				@Override
				public Optional<String> objection(Principal principal, String action, ResourceRef resource) {
					throw new IllegalStateException("missing data");
				}

			};
			List<String> consulted = new ArrayList<>();
			assertThat(pointFor(Set.of(OWN_UPDATES), broken, rule("after", false, consulted))
				.decide(alice(), UPDATE, widget(ALICE, TENANT))).isEqualTo(Decision.of(Reason.EVALUATION_ERROR, "broken"));
			assertThat(consulted).isEmpty();
		}

		@Test
		@DisplayName("a rule for another resource type is not consulted")
		void ruleForAnotherType() {
			List<String> consulted = new ArrayList<>();
			ResourceRef gadget = ResourceRef.of("widgets.gadget", UUID.randomUUID(), TENANT).ownedBy(ALICE);
			assertThat(pointFor(Set.of(OWN_UPDATES), rule("widgets-only", true, consulted)).decide(alice(), UPDATE, gadget)
				.allowed()).isTrue();
			assertThat(consulted).isEmpty();
		}

		@Test
		@DisplayName("does not narrow the system")
		void systemIsNotNarrowed() {
			List<String> consulted = new ArrayList<>();
			assertThat(pointFor(Set.of(), rule("locked", true, consulted))
				.decide(DefaultDecisionPointTest.system(), UPDATE, widget(BOB, TENANT)).reason()).isEqualTo(Reason.SYSTEM);
		}

	}

	@Test
	@DisplayName("a grant's short form is accepted by the audit log, which refuses anything that looks like an email address")
	void grantDescriptionsFitTheAuditLog() {
		for (Grant grant : List.of(OWN_UPDATES, ALL_UPDATES, Grant.deny(UPDATE))) {
			com.kibootia.backend.audit.AuditEntry.of("access.role.updated", "role", UUID.randomUUID())
				.withChange("grants", "+" + grant.describe());
			assertThat(grant.describe()).doesNotContain("@");
		}
	}

	@Test
	@DisplayName("reasons agree with allow, and every code is in the authorization namespace")
	void reasonsAreConsistent() {
		for (Reason reason : Reason.values()) {
			assertThat(Decision.of(reason).allowed()).isEqualTo(reason == Reason.GRANTED || reason == Reason.SYSTEM);
			assertThat(reason.code()).startsWith("authorization.");
		}
	}

}
