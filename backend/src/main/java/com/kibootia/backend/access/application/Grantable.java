package com.kibootia.backend.access.application;

import java.util.Collection;

import org.springframework.security.access.AccessDeniedException;

import com.kibootia.backend.platform.authorization.AccessFilter;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Grant;
import com.kibootia.backend.platform.authorization.Reach;

/**
 * The "only what you hold" rule (ADR 0054, rule 7), shared by role editing and role assignment: a principal can put a
 * grant into a role, or give a role to someone, only if they hold every allow it carries, at least as far as it
 * reaches. Holding {@code notes.notes.update} for one's own notes does not let anyone grant it for everyone's. A deny
 * grant takes access away, so it needs only that the principal holds the action at all.
 *
 * <p>Package-private on purpose: callers are use cases that also check their own permission directly, which is what
 * architecture rule 7 sees.
 */
final class Grantable {

	private Grantable() {
	}

	static void requireHeld(Authorization authorization, Collection<Grant> grants) {
		for (Grant grant : grants) {
			if (!grant.allows()) {
				authorization.require(grant.action());
				continue;
			}
			AccessFilter held = authorization.requireFilter(grant.action());
			if (!covers(held, grant.reach(), authorization)) {
				throw new AccessDeniedException("Not allowed to grant " + grant.describe() + " beyond one's own reach");
			}
		}
	}

	private static boolean covers(AccessFilter held, Reach wanted, Authorization authorization) {
		return switch (held) {
			case AccessFilter.Everything everything -> true;
			case AccessFilter.Nothing nothing -> false;
			case AccessFilter.AnyOf any -> wanted instanceof Reach.Relation(String relation) && any.alternatives()
				.stream()
				.anyMatch((alternative) -> alternative instanceof AccessFilter.RelatedTo(String name, java.util.UUID subject)
						&& name.equals(relation) && subject.equals(authorization.currentUserId()));
		};
	}

}
