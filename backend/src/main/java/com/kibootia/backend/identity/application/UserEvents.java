package com.kibootia.backend.identity.application;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.identity.UserErased;
import com.kibootia.backend.platform.events.DurableEventListener;

/**
 * The reactions to what happens to a user (ADR 0031, ADR 0041): everything that must reach Keycloak. They are durable
 * handlers, so Keycloak being down delays them instead of losing them, and each is written to be run again with the
 * same effect, because delivery is at least once.
 *
 * <p>The methods are not public, so they are not use cases: they are reached only through the event, as the system, in
 * the event's tenant. None of them holds a database transaction while Keycloak is called (ADR 0058): each is declared
 * {@code transactional = false} and reads or writes the database in short transactions of its own.
 */
@Component
class UserEvents {

	private static final Logger log = LoggerFactory.getLogger(UserEvents.class);

	private final UserProfileStore profiles;

	private final IdentityProviderAdmin keycloak;

	private final RoleAssignments roleAssignments;

	private final AuditLog auditLog;

	private final TransactionTemplate transactions;

	UserEvents(UserProfileStore profiles, IdentityProviderAdmin keycloak, RoleAssignments roleAssignments,
			AuditLog auditLog, PlatformTransactionManager transactionManager) {
		this.profiles = profiles;
		this.keycloak = keycloak;
		this.roleAssignments = roleAssignments;
		this.auditLog = auditLog;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** The profile as it is now, read in a short transaction that is over before Keycloak is called. */
	private Optional<UserProfile> profileOf(UUID id) {
		return this.transactions.execute((status) -> this.profiles.find(id));
	}

	/** An invitation, or a repeated one: Keycloak emails the set-password link. */
	@DurableEventListener(transactional = false)
	void on(UserInvited event) {
		Optional<UserProfile> profile = profileOf(event.userId());
		if (profile.isEmpty() || !profile.get().canSignIn()) {
			log.info("Not sending the invitation to {}: the user is gone or can no longer sign in", event.userId());
			return;
		}
		this.keycloak.sendInvitation(event.userId());
	}

	/**
	 * An invitation failed after it created a Keycloak account, and the account could not be removed then: remove it now,
	 * unless the user has a profile after all (the invitation succeeded in a way the caller did not see), in which case the
	 * account is theirs. Deleting an account that is already gone is harmless.
	 */
	@DurableEventListener(transactional = false)
	void on(InvitationAbandoned event) {
		if (profileOf(event.userId()).isPresent()) {
			log.info("Keycloak account {} belongs to a profile after all; not removing it", event.userId());
			return;
		}
		this.keycloak.delete(event.userId());
	}

	/** A user registered without a verified address: Keycloak sends the verification mail. */
	@DurableEventListener(transactional = false)
	void on(VerificationEmailRequested event) {
		Optional<UserProfile> profile = profileOf(event.userId());
		if (profile.isEmpty() || profile.get().deleted()) {
			return;
		}
		if (this.keycloak.find(event.userId()).map(IdentityProviderAdmin.Account::emailVerified).orElse(true)) {
			return;
		}
		this.keycloak.sendVerificationEmail(event.userId());
	}

	/**
	 * Brings the Keycloak account in line with the profile as it is now, whatever happened in between: enabled only for a
	 * user who is neither disabled nor deleted. A user who should not sign in also has their sessions ended, every time,
	 * so a repeated delivery cannot leave one open.
	 */
	@DurableEventListener(transactional = false)
	void on(UserAccountChanged event) {
		UUID id = event.userId();
		Optional<UserProfile> profile = profileOf(id);
		if (profile.isEmpty()) {
			return;
		}
		boolean shouldBeEnabled = profile.get().canSignIn();
		Optional<IdentityProviderAdmin.Account> account = this.keycloak.find(id);
		if (account.isEmpty()) {
			log.warn("Keycloak has no account {} to bring in line with its profile", id);
			return;
		}
		if (account.get().enabled() != shouldBeEnabled) {
			this.keycloak.setEnabled(id, shouldBeEnabled);
		}
		if (!shouldBeEnabled) {
			this.keycloak.endSessions(id);
		}
	}

	/**
	 * Erases the user: the Keycloak account first, then the profile and the roles. In that order, so that until the
	 * account is gone the profile still refuses the user; a failure after the first step is retried and deleting an
	 * account that is gone is harmless. The audit log keeps the user's ID only.
	 */
	@DurableEventListener(transactional = false)
	void on(UserErased event) {
		UUID id = event.userId();
		this.keycloak.delete(id);
		// The database part is one short transaction of its own: the roles, the profile and the audit entry go together.
		this.transactions.executeWithoutResult((status) -> {
			this.roleAssignments.removeAll(id);
			if (this.profiles.find(id).isPresent()) {
				this.profiles.erase(id);
				this.auditLog.record(AuditEntry.of("identity.user.erased", "user", id));
			}
		});
	}

}
