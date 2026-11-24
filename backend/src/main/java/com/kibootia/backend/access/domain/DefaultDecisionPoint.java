package com.kibootia.backend.access.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kibootia.backend.platform.authorization.AccessFilter;
import com.kibootia.backend.platform.authorization.Decision;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.Reason;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceRule;

/**
 * The Java evaluation behind the decision point (ADR 0027, contract version 2 of ADR 0055).
 *
 * <p>The combination rule, in order:
 * <ol>
 * <li>An action must be declared; anything else is a programming error and throws.</li>
 * <li>The system may do anything, in the tenant it runs in. It is a principal created only by
 * {@code Authorization#runAsSystem}, with a recorded reason.</li>
 * <li>A user who has not verified their email address holds only the actions marked
 * {@code allowedBeforeVerification} (ADR 0041).</li>
 * <li>The <em>reach</em> of the action comes from the user's grants: holding {@code <action>-any}, or holding an
 * {@code <action>} that has no {@code -any} variant, reaches every record of the tenant; holding an {@code <action>}
 * that has one reaches the records the user owns. Holding neither is {@link Reason#NO_GRANT}.</li>
 * <li>For one record: a record of another tenant is refused; a record outside the reach is refused.</li>
 * <li>Every module rule that applies is consulted, and any objection refuses. A rule can only narrow: it is never
 * consulted where grants refused. A rule that throws refuses, as {@link Reason#EVALUATION_ERROR}.</li>
 * </ol>
 *
 * <p>Without a record (an action on the collection) the decision is made from grants alone: any reach allows.
 */
public final class DefaultDecisionPoint implements PolicyDecisionPoint {

	private static final Logger log = LoggerFactory.getLogger(DefaultDecisionPoint.class);

	private final PermissionRegistry registry;

	private final PermissionSource source;

	private final List<ResourceRule> rules;

	public DefaultDecisionPoint(PermissionRegistry registry, PermissionSource source, List<ResourceRule> rules) {
		this.registry = registry;
		this.source = source;
		this.rules = List.copyOf(rules);
	}

	/** How far a user's grants for one action reach. */
	private record Reach(Kind kind, String grant) {

		enum Kind {

			NONE, OWN, ALL

		}

		static final Reach NONE = new Reach(Kind.NONE, "");

	}

	@Override
	public Decision decide(Principal principal, String action, ResourceRef resource) {
		requireRegistered(action);
		if (principal instanceof Principal.System) {
			return Decision.of(Reason.SYSTEM, "system");
		}
		Principal.User user = (Principal.User) principal;
		if (!user.emailVerified() && !this.registry.isAllowedBeforeVerification(action)) {
			return Decision.of(Reason.EMAIL_NOT_VERIFIED);
		}
		Reach reach = reachOf(user, action);
		if (reach.kind() == Reach.Kind.NONE) {
			return Decision.of(Reason.NO_GRANT);
		}
		if (resource == null) {
			return Decision.of(Reason.GRANTED, reach.grant());
		}
		if (!user.tenantId().equals(resource.tenantId())) {
			return Decision.of(Reason.OTHER_TENANT);
		}
		if (reach.kind() == Reach.Kind.OWN && !resource.hasRelation(ResourceRef.OWNER, user.id())) {
			return Decision.of(Reason.OUT_OF_REACH, reach.grant());
		}
		return narrowed(principal, action, resource).orElseGet(() -> Decision.of(Reason.GRANTED, reach.grant()));
	}

	@Override
	public AccessFilter filter(Principal principal, String action) {
		requireRegistered(action);
		if (principal instanceof Principal.System) {
			return AccessFilter.EVERYTHING;
		}
		Principal.User user = (Principal.User) principal;
		if (!user.emailVerified() && !this.registry.isAllowedBeforeVerification(action)) {
			return AccessFilter.NOTHING;
		}
		return switch (reachOf(user, action).kind()) {
			case NONE -> AccessFilter.NOTHING;
			case OWN -> AccessFilter.relatedTo(ResourceRef.OWNER, user.id());
			case ALL -> AccessFilter.EVERYTHING;
		};
	}

	@Override
	public boolean isSensitive(String action) {
		return this.registry.isSensitive(action);
	}

	private Reach reachOf(Principal.User user, String action) {
		Set<String> held = this.source.permissionsOf(user);
		String any = action + PermissionRegistry.ANY_SUFFIX;
		if (held.contains(any)) {
			return new Reach(Reach.Kind.ALL, any);
		}
		if (held.contains(action)) {
			return new Reach(this.registry.hasAnyVariant(action) ? Reach.Kind.OWN : Reach.Kind.ALL, action);
		}
		return Reach.NONE;
	}

	/** The first objection of the rules that apply, as a refusal; empty when none objects. */
	private Optional<Decision> narrowed(Principal principal, String action, ResourceRef resource) {
		for (ResourceRule rule : this.rules) {
			try {
				if (!rule.appliesTo(resource.type(), action)) {
					continue;
				}
				Optional<String> objection = rule.objection(principal, action, resource);
				if (objection.isPresent()) {
					log.debug("Rule {} refuses {} on {}: {}", rule.name(), action, resource, objection.get());
					return Optional.of(Decision.of(Reason.REFUSED_BY_RULE, rule.name()));
				}
			}
			catch (RuntimeException ex) {
				log.error("Rule {} failed while deciding {} on {}; refusing", rule.name(), action, resource, ex);
				return Optional.of(Decision.of(Reason.EVALUATION_ERROR, rule.name()));
			}
		}
		return Optional.empty();
	}

	private void requireRegistered(String action) {
		if (!this.registry.isRegistered(action)) {
			throw new IllegalStateException("'" + action + "' is not a declared permission");
		}
	}

}
