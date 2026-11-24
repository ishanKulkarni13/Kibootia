package com.kibootia.backend.access.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kibootia.backend.platform.authorization.AccessFilter;
import com.kibootia.backend.platform.authorization.Decision;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.Principal;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.Reason;
import com.kibootia.backend.platform.authorization.ResourceRef;
import com.kibootia.backend.platform.authorization.ResourceRule;

/**
 * The Java evaluation behind the decision point (ADR 0027, contract version 2 of ADR 0055, the grant model of ADR 0054).
 *
 * <p>The combination rule, in order:
 * <ol>
 * <li>An action must be declared; anything else is a programming error and throws.</li>
 * <li>The system may do anything, in the tenant it runs in. It is a principal created only by
 * {@code Authorization#runAsSystem}, with a recorded reason.</li>
 * <li>A user who has not verified their email address holds only the actions marked
 * {@code allowedBeforeVerification} (ADR 0041).</li>
 * <li>The user's grants for the action are collected from all their roles. Any deny grant decides
 * ({@link Reason#DENIED_BY_GRANT}). With no allow grant: {@link Reason#NO_GRANT}.</li>
 * <li>For one record: a record of another tenant is refused; then an allow grant must reach the record, either with
 * reach {@code all} or by a relation the user has to it ({@link Reason#OUT_OF_REACH} otherwise).</li>
 * <li>Every module rule that applies is consulted, and any objection refuses. A rule can only narrow: it is never
 * consulted where grants refused. A rule that throws refuses, as {@link Reason#EVALUATION_ERROR}.</li>
 * </ol>
 *
 * <p>Without a record (an action on the collection) the decision is made from grants alone: any allow grant allows.
 * The decision names the grant that decided, for example {@code notes.notes.update/relation:owner}.
 */
public final class DefaultDecisionPoint implements PolicyDecisionPoint {

	private static final Logger log = LoggerFactory.getLogger(DefaultDecisionPoint.class);

	/** Prefer naming the widest grant: "all" before any relation, relations by name. */
	private static final Comparator<Grant> WIDEST_FIRST = Comparator
		.comparing((Grant grant) -> !(grant.reach() instanceof Reach.All))
		.thenComparing((grant) -> grant.reach().code());

	private final PermissionRegistry registry;

	private final PermissionSource source;

	private final List<ResourceRule> rules;

	public DefaultDecisionPoint(PermissionRegistry registry, PermissionSource source, List<ResourceRule> rules) {
		this.registry = registry;
		this.source = source;
		this.rules = List.copyOf(rules);
	}

	/** A user's grants for one action: whether a deny applies, and the allows sorted widest first. */
	private record Held(Optional<Grant> deny, List<Grant> allows) {

		boolean nothing() {
			return this.allows.isEmpty();
		}

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
		Held held = heldBy(user, action);
		if (held.deny().isPresent()) {
			return Decision.of(Reason.DENIED_BY_GRANT, held.deny().get().describe());
		}
		if (held.nothing()) {
			return Decision.of(Reason.NO_GRANT);
		}
		if (resource == null) {
			return Decision.of(Reason.GRANTED, held.allows().getFirst().describe());
		}
		if (!user.tenantId().equals(resource.tenantId())) {
			return Decision.of(Reason.OTHER_TENANT);
		}
		Optional<Grant> reaching = held.allows().stream().filter((grant) -> reaches(grant, user, resource)).findFirst();
		if (reaching.isEmpty()) {
			return Decision.of(Reason.OUT_OF_REACH, held.allows().getFirst().describe());
		}
		return narrowed(principal, action, resource)
			.orElseGet(() -> Decision.of(Reason.GRANTED, reaching.get().describe()));
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
		Held held = heldBy(user, action);
		if (held.deny().isPresent() || held.nothing()) {
			return AccessFilter.NOTHING;
		}
		if (held.allows().getFirst().reach() instanceof Reach.All) {
			return AccessFilter.EVERYTHING;
		}
		List<AccessFilter.Alternative> alternatives = new ArrayList<>();
		for (Grant grant : held.allows()) {
			if (grant.reach() instanceof Reach.Relation(String relation)) {
				alternatives.add(new AccessFilter.RelatedTo(relation, user.id()));
			}
		}
		return new AccessFilter.AnyOf(alternatives);
	}

	@Override
	public boolean isSensitive(String action) {
		return this.registry.isSensitive(action);
	}

	private Held heldBy(Principal.User user, String action) {
		Set<Grant> grants = this.source.grantsOf(user);
		Optional<Grant> deny = Optional.empty();
		List<Grant> allows = new ArrayList<>();
		for (Grant grant : grants) {
			if (!grant.action().equals(action) || !this.registry.isValid(grant)) {
				continue;
			}
			if (grant.allows()) {
				allows.add(grant);
			}
			else {
				deny = Optional.of(grant);
			}
		}
		allows.sort(WIDEST_FIRST);
		return new Held(deny, allows);
	}

	private static boolean reaches(Grant grant, Principal.User user, ResourceRef resource) {
		return switch (grant.reach()) {
			case Reach.All all -> true;
			case Reach.Relation(String relation) -> resource.hasRelation(relation, user.id());
		};
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
