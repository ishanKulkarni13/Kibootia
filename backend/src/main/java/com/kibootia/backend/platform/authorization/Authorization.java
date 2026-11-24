package com.kibootia.backend.platform.authorization;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The entry point for authorization checks inside use cases (ADR 0044, contract version 2 of ADR 0055).
 *
 * <ul>
 * <li>An action on the collection (create, an administrative action) is declared with {@link RequiresPermission}, or
 * checked with {@link #require(String)}.</li>
 * <li>An action on one record is checked after loading it, with {@link #require(String, Resource)}; for the read that
 * loads it, {@link #requireVisible} applies the rule for invisible records.</li>
 * <li>A list asks {@link #requireFilter(String)} and turns the answer into its query condition.</li>
 * </ul>
 *
 * <p>Every check goes through the decision point and is recorded by {@link DecisionRecorder}. A refusal is an {@link
 * AccessDeniedException}, which the web layer answers with 403; a caller who has not verified their email address gets
 * {@link EmailNotVerifiedException} instead, because verifying is what they can do about it.
 *
 * <p><strong>403 or 404</strong> (OQ-87, decided in ADR 0064): a caller who holds no grant for the action at all is
 * refused with 403, and that is decided without reading any record. A caller who holds the action but whose grants do
 * not reach the record, and a record that does not exist, both answer 404, so the answer never says whether an ID exists.
 */
@Component
public class Authorization {

	private static final Logger log = LoggerFactory.getLogger(Authorization.class);

	private static final ThreadLocal<Principal.System> SYSTEM = new ThreadLocal<>();

	private final PolicyDecisionPoint decisionPoint;

	private final DecisionRecorder recorder;

	/** With no decision point (the {@code access} module is absent) everything is denied. */
	Authorization(ObjectProvider<PolicyDecisionPoint> decisionPoint, DecisionRecorder recorder) {
		this.decisionPoint = decisionPoint.getIfAvailable(Authorization::denyAll);
		this.recorder = recorder;
	}

	private static PolicyDecisionPoint denyAll() {
		return new PolicyDecisionPoint() {

			@Override
			public Decision decide(Principal principal, String action, ResourceRef resource) {
				return Decision.of(Reason.NO_DECISION_POINT);
			}

			@Override
			public AccessFilter filter(Principal principal, String action) {
				return AccessFilter.NOTHING;
			}

		};
	}

	// --- decisions ---------------------------------------------------------------------------------------------------

	/** The decision for an action on the collection, with its reason. */
	public Decision decide(String action) {
		return decide(action, (ResourceRef) null);
	}

	/** The decision for an action on one record, with its reason. */
	public Decision decide(String action, Resource resource) {
		return decide(action, resource.resourceRef());
	}

	private Decision decide(String action, ResourceRef resource) {
		Principal principal = principal();
		Decision decision = this.decisionPoint.decide(principal, action, resource);
		this.recorder.record(principal, action, resource, decision, this.decisionPoint.isSensitive(action));
		return decision;
	}

	/** Whether the principal holds the action at all: on the collection, or on at least some records. */
	public boolean can(String action) {
		return decide(action).allowed();
	}

	/** Whether the principal may perform the action on this record. */
	public boolean can(String action, Resource resource) {
		return decide(action, resource).allowed();
	}

	public void require(String action) {
		Decision decision = decide(action);
		if (!decision.allowed()) {
			throw denied(action);
		}
	}

	public void require(String action, Resource resource) {
		Decision decision = decide(action, resource);
		if (!decision.allowed()) {
			throw denied(action);
		}
	}

	/**
	 * The record, when the principal may perform the action on it; the rule for invisible records applied. Without any
	 * grant for the action: 403, decided before the record is looked at. With a grant that does not reach the record, or
	 * with no record: the exception {@code notFound} supplies, the same in both cases.
	 */
	public <T extends Resource> T requireVisible(String action, Optional<T> record,
			Supplier<? extends RuntimeException> notFound) {
		if (requireFilter(action).isNothing()) {
			throw denied(action);
		}
		T found = record.orElseThrow(notFound);
		if (!can(action, found)) {
			throw notFound.get();
		}
		return found;
	}

	/**
	 * For an operation on the caller's own account that needs no permission but is not open to someone who has not
	 * verified their email address (ADR 0041).
	 */
	public void requireVerified() {
		if (principal() instanceof Principal.User user && !user.emailVerified()) {
			throw new EmailNotVerifiedException();
		}
	}

	// --- lists -------------------------------------------------------------------------------------------------------

	/** Which records the principal may perform the action on; {@link AccessFilter#NOTHING} when none. */
	public AccessFilter filter(String action) {
		return this.decisionPoint.filter(principal(), action);
	}

	/** Like {@link #filter} but refuses with 403 when the principal may perform the action on no record at all. */
	public AccessFilter requireFilter(String action) {
		AccessFilter filter = filter(action);
		if (filter.isNothing()) {
			// Recorded as a refusal of the action on the collection, so a refused list leaves a record too.
			decide(action);
			throw denied(action);
		}
		return filter;
	}

	// --- the principal -----------------------------------------------------------------------------------------------

	/** Whether the signed-in user's email address is verified, as the token says (ADR 0041). The system counts as verified. */
	public boolean isEmailVerified() {
		return !(principal() instanceof Principal.User user) || user.emailVerified();
	}

	/** The ID of the principal, for ownership conditions and audit columns; the nil UUID for the system. */
	public UUID currentUserId() {
		return principal().id();
	}

	/**
	 * Runs work that no user started (a job, an event handler) with system authority. The reason is required, and every
	 * decision record of the work names it, so the use of system authority is visible and reviewable (ADR 0055, point
	 * 7). The tenant must already be bound. Calls nest: the innermost reason applies, and the previous one is restored.
	 */
	public <T> T runAsSystem(String reason, Callable<T> work) {
		Principal.System system = new Principal.System(TenantContext.require(), reason);
		Principal.System previous = SYSTEM.get();
		SYSTEM.set(system);
		log.debug("Acting as the system: {}", reason);
		try {
			return work.call();
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		finally {
			if (previous == null) {
				SYSTEM.remove();
			}
			else {
				SYSTEM.set(previous);
			}
		}
	}

	public void runAsSystem(String reason, Runnable work) {
		runAsSystem(reason, () -> {
			work.run();
			return null;
		});
	}

	/**
	 * The principal of the current work: the system inside {@link #runAsSystem}, otherwise the user of the bearer token
	 * in the bound tenant. Refuses when there is neither, or when the token's subject is not a user ID.
	 */
	Principal principal() {
		Principal.System system = SYSTEM.get();
		if (system != null) {
			return system;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
			throw new AccessDeniedException("There is no signed-in user");
		}
		UUID tenant = TenantContext.current().orElseThrow(() -> new AccessDeniedException("There is no tenant"));
		try {
			return new Principal.User(UUID.fromString(token.getSubject()), tenant,
					Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new AccessDeniedException("The token does not identify a user");
		}
	}

	/**
	 * The refusal for an action. A caller who has not verified their email address is told that, because verifying is
	 * what they can do about it; everyone else gets a plain denial.
	 */
	AccessDeniedException denied(String action) {
		return isEmailVerified() ? new AccessDeniedException("Not allowed: " + action) : new EmailNotVerifiedException();
	}

}
