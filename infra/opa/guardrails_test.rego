# Tests of the guardrail policy, run with `opa test infra/opa` (and in CI).
package kibootia.guardrails_test

import data.kibootia.guardrails

user := {"kind": "user", "id": "11111111-1111-7111-8111-111111111111", "tenant": "00000000-0000-7000-8000-000000000001"}

test_no_objection_by_default if {
	count(guardrails.deny) == 0 with input as {"action": "identity.users.erase", "principal": user, "resource": null}
}

test_a_frozen_action_is_refused if {
	"identity.users.erase is frozen" in guardrails.deny with input as {"action": "identity.users.erase", "principal": user, "resource": null}
		with data.guardrails_config.frozen_actions as ["identity.users.erase"]
}

test_freezing_one_action_leaves_the_others_alone if {
	count(guardrails.deny) == 0 with input as {"action": "audit.entries.purge", "principal": user, "resource": null}
		with data.guardrails_config.frozen_actions as ["identity.users.erase"]
}

test_a_blocked_principal_is_refused if {
	"the principal is blocked" in guardrails.deny with input as {"action": "notes.notes.purge", "principal": user, "resource": null}
		with data.guardrails_config.blocked_principals as [user.id]
}
