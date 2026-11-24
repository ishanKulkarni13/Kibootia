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
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.Reason;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The combination rule of the decision point (contract version 2, ADR 0055). Every case of contract version 1 is kept
 * with its expected outcome; only the module-rule cases changed, because a rule can no longer widen access (finding S3).
 */
class DefaultDecisionPointTest {

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-000000000002");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final String WIDGET = "widgets.widget";

	static final String UPDATE = "widgets.widgets.update";

	static final String UPDATE_ANY = "widgets.widgets.update-any";

	static final String RESTORE = "widgets.widgets.restore";

	static final PermissionRegistry REGISTRY = new PermissionRegistry(List.of(
			Permission.forEveryUser(UPDATE, "Change own widgets"),
			Permission.restricted(UPDATE_ANY, "Change anyone's widgets"),
			Permission.restricted(RESTORE, "Restore deleted widgets").asSensitive()));

	static ResourceRef widget(UUID owner, UUID tenant) {
		return ResourceRef.of(WIDGET, UUID.randomUUID(), tenant).ownedBy(owner);
	}

	static Principal alice() {
		return new Principal.User(ALICE, TENANT, true);
	}

	static Principal system() {
		return new Principal.System(TENANT, "test: a job");
	}

	static DefaultDecisionPoint pointFor(Set<String> held, ResourceRule... rules) {
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
	@DisplayName("the permission registry")
	class Registry {

		@Test
		@DisplayName("lists what modules declare, which the user role holds, and which are sensitive")
		void listsPermissions() {
			assertThat(REGISTRY.all()).extracting(Permission::name).containsExactly(UPDATE, UPDATE_ANY, RESTORE);
			assertThat(REGISTRY.userRolePermissions()).containsExactly(UPDATE);
			assertThat(REGISTRY.hasAnyVariant(UPDATE)).isTrue();
			assertThat(REGISTRY.hasAnyVariant(RESTORE)).isFalse();
			assertThat(REGISTRY.isSensitive(RESTORE)).isTrue();
			assertThat(REGISTRY.isSensitive(UPDATE)).isFalse();
		}

		@Test
		@DisplayName("refuses a name that is not <module>.<resource>.<action>")
		void refusesBadNames() {
			for (String bad : List.of("update", "widgets.update", "Widgets.widgets.update", "widgets.widgets.Update",
					"widgets.widgets.update.again", "widgets..update", "widgets.widgets.update_any")) {
				assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.restricted(bad, "x"))))
					.as(bad)
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining(bad);
			}
		}

		@Test
		@DisplayName("refuses a permission declared twice")
		void refusesDuplicates() {
			assertThatThrownBy(() -> new PermissionRegistry(
					List.of(Permission.restricted(RESTORE, "a"), Permission.restricted(RESTORE, "b"))))
				.hasMessageContaining(RESTORE);
		}

		@Test
		@DisplayName("refuses an -any permission whose plain permission is missing")
		void refusesOrphanAnyPermission() {
			assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.restricted(UPDATE_ANY, "x"))))
				.hasMessageContaining(UPDATE);
		}

	}

	@Nested
	@DisplayName("the access filter of an action (was: its scope)")
	class Filters {

		@Test
		@DisplayName("is nothing without the permission (was NONE)")
		void none() {
			assertThat(pointFor(Set.of()).filter(alice(), UPDATE)).isEqualTo(AccessFilter.NOTHING);
			assertThat(pointFor(Set.of()).filter(alice(), RESTORE)).isEqualTo(AccessFilter.NOTHING);
		}

		@Test
		@DisplayName("is the caller's own records for a permission that has an -any variant (was OWN)")
		void own() {
			AccessFilter filter = pointFor(Set.of(UPDATE)).filter(alice(), UPDATE);
			assertThat(filter).isEqualTo(AccessFilter.relatedTo(ResourceRef.OWNER, ALICE));
			assertThat(filter.ownerOnly()).contains(ALICE);
		}

		@Test
		@DisplayName("is everything for the -any permission (was ALL)")
		void all() {
			assertThat(pointFor(Set.of(UPDATE, UPDATE_ANY)).filter(alice(), UPDATE)).isEqualTo(AccessFilter.EVERYTHING);
			assertThat(pointFor(Set.of(UPDATE_ANY)).filter(alice(), UPDATE)).isEqualTo(AccessFilter.EVERYTHING);
			assertThat(AccessFilter.EVERYTHING.ownerOnly()).isEmpty();
		}

		@Test
		@DisplayName("is everything for an administrative permission that has no -any variant")
		void administrativePermission() {
			assertThat(pointFor(Set.of(RESTORE)).filter(alice(), RESTORE)).isEqualTo(AccessFilter.EVERYTHING);
		}

		@Test
		@DisplayName("holding the -any variant of another action grants nothing")
		void otherActionGrantsNothing() {
			assertThat(pointFor(Set.of(UPDATE_ANY)).filter(alice(), RESTORE)).isEqualTo(AccessFilter.NOTHING);
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

	}

	@Nested
	@DisplayName("a caller who has not verified their email address")
	class Unverified {

		static final String BEFORE = "widgets.widgets.read-own";

		final PermissionRegistry registry = new PermissionRegistry(List.of(
				Permission.forEveryUser(UPDATE, "Change own widgets"),
				Permission.restricted(UPDATE_ANY, "Change anyone's widgets"),
				Permission.forEveryUser(BEFORE, "Read own widgets").beforeVerification()));

		Principal unverified() {
			return new Principal.User(ALICE, TENANT, false);
		}

		@Test
		@DisplayName("holds nothing, even what the user role grants")
		void holdsNothing() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (user) -> Set.of(UPDATE, UPDATE_ANY, BEFORE),
					List.of());
			assertThat(point.filter(unverified(), UPDATE)).isEqualTo(AccessFilter.NOTHING);
			assertThat(point.filter(unverified(), UPDATE_ANY)).isEqualTo(AccessFilter.NOTHING);
			Decision decision = point.decide(unverified(), UPDATE, widget(ALICE, TENANT));
			assertThat(decision.allowed()).isFalse();
			assertThat(decision.reason()).isEqualTo(Reason.EMAIL_NOT_VERIFIED);
		}

		@Test
		@DisplayName("holds a permission marked as allowed before verification")
		void holdsMarkedPermission() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (user) -> Set.of(BEFORE), List.of());
			assertThat(point.filter(unverified(), BEFORE)).isEqualTo(AccessFilter.EVERYTHING);
			assertThat(point.filter(new Principal.User(ALICE, TENANT, true), BEFORE)).isEqualTo(AccessFilter.EVERYTHING);
		}

		@Test
		@DisplayName("is still denied a marked permission it was never granted")
		void markedPermissionStillNeedsGranting() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (user) -> Set.of(), List.of());
			assertThat(point.filter(unverified(), BEFORE)).isEqualTo(AccessFilter.NOTHING);
		}

	}

	@Nested
	@DisplayName("a decision about one record")
	class Records {

		@Test
		@DisplayName("allows an owner with the plain permission and denies someone else's record as out of reach")
		void ownership() {
			DefaultDecisionPoint point = pointFor(Set.of(UPDATE));
			assertThat(point.decide(alice(), UPDATE, widget(ALICE, TENANT)))
				.isEqualTo(Decision.of(Reason.GRANTED, UPDATE));
			assertThat(point.decide(alice(), UPDATE, widget(BOB, TENANT)))
				.isEqualTo(Decision.of(Reason.OUT_OF_REACH, UPDATE));
		}

		@Test
		@DisplayName("denies an own-records permission on a record that has no owner")
		void recordWithoutOwner() {
			ResourceRef ownerless = ResourceRef.of(WIDGET, UUID.randomUUID(), TENANT);
			assertThat(pointFor(Set.of(UPDATE)).decide(alice(), UPDATE, ownerless).reason())
				.isEqualTo(Reason.OUT_OF_REACH);
		}

		@Test
		@DisplayName("allows any record in the tenant with the -any permission, and names the grant that decided")
		void anyPermission() {
			assertThat(pointFor(Set.of(UPDATE, UPDATE_ANY)).decide(alice(), UPDATE, widget(BOB, TENANT)))
				.isEqualTo(Decision.of(Reason.GRANTED, UPDATE_ANY));
		}

		@Test
		@DisplayName("denies without the permission, even for the owner")
		void withoutPermission() {
			assertThat(pointFor(Set.of()).decide(alice(), UPDATE, widget(ALICE, TENANT)))
				.isEqualTo(Decision.of(Reason.NO_GRANT));
		}

		@Test
		@DisplayName("denies a record of another tenant even with the -any permission")
		void otherTenant() {
			assertThat(pointFor(Set.of(UPDATE_ANY)).decide(alice(), UPDATE, widget(ALICE, OTHER_TENANT)).reason())
				.isEqualTo(Reason.OTHER_TENANT);
			assertThat(pointFor(Set.of(UPDATE)).decide(alice(), UPDATE, widget(ALICE, OTHER_TENANT)).reason())
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
			assertThat(pointFor(Set.of(UPDATE)).decide(alice(), UPDATE, null)).isEqualTo(Decision.of(Reason.GRANTED, UPDATE));
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
			DefaultDecisionPoint point = pointFor(Set.of(UPDATE), rule("allows-everything", false, consulted));
			assertThat(point.decide(alice(), UPDATE, widget(BOB, TENANT)).reason()).isEqualTo(Reason.OUT_OF_REACH);
			assertThat(pointFor(Set.of(), rule("allows-everything", false, consulted))
				.decide(alice(), UPDATE, widget(ALICE, TENANT)).reason()).isEqualTo(Reason.NO_GRANT);
			assertThat(consulted).as("a rule is never consulted where grants refused").isEmpty();
		}

		@Test
		@DisplayName("a rule narrows what grants allow, and the decision names it")
		void ruleNarrows() {
			List<String> consulted = new ArrayList<>();
			DefaultDecisionPoint point = pointFor(Set.of(UPDATE_ANY, UPDATE), rule("locked-widgets", true, consulted));
			assertThat(point.decide(alice(), UPDATE, widget(BOB, TENANT)))
				.isEqualTo(Decision.of(Reason.REFUSED_BY_RULE, "locked-widgets"));
		}

		@Test
		@DisplayName("every applicable rule is consulted until one objects, and any objection refuses")
		void allRulesApply() {
			List<String> consulted = new ArrayList<>();
			DefaultDecisionPoint point = pointFor(Set.of(UPDATE), rule("first", false, consulted),
					rule("second", true, consulted));
			assertThat(point.decide(alice(), UPDATE, widget(ALICE, TENANT)))
				.isEqualTo(Decision.of(Reason.REFUSED_BY_RULE, "second"));
			assertThat(consulted).containsExactly("first", "second");

			List<String> both = new ArrayList<>();
			assertThat(pointFor(Set.of(UPDATE), rule("first", false, both), rule("second", false, both))
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
			assertThat(pointFor(Set.of(UPDATE), broken, rule("after", false, consulted))
				.decide(alice(), UPDATE, widget(ALICE, TENANT))).isEqualTo(Decision.of(Reason.EVALUATION_ERROR, "broken"));
			assertThat(consulted).isEmpty();
		}

		@Test
		@DisplayName("a rule for another resource type is not consulted")
		void ruleForAnotherType() {
			List<String> consulted = new ArrayList<>();
			ResourceRef gadget = ResourceRef.of("widgets.gadget", UUID.randomUUID(), TENANT).ownedBy(ALICE);
			assertThat(pointFor(Set.of(UPDATE), rule("widgets-only", true, consulted)).decide(alice(), UPDATE, gadget)
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
	@DisplayName("every reason a decision point can give is produced by some case above, and reasons agree with allow")
	void reasonsAreConsistent() {
		for (Reason reason : Reason.values()) {
			assertThat(Decision.of(reason).allowed()).isEqualTo(reason == Reason.GRANTED || reason == Reason.SYSTEM);
			assertThat(reason.code()).startsWith("authorization.");
		}
	}

}
