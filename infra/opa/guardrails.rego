# Kibootia's guardrail policy for Open Policy Agent (ADR 0066).
#
# Kibootia asks this policy only about the actions listed in KIBOOTIA_POLICY_OPA_ACTIONS, and only after its own roles
# have allowed the action. The answer is the set `deny`: every element is a reason to refuse, and an empty set means no
# objection. A policy here can therefore narrow what roles allow, never widen it.
#
# The input, identifiers only:
#   input.action     the action, for example "identity.users.erase"
#   input.principal  {"kind": "user", "id": "<uuid>", "tenant": "<uuid>"}
#   input.resource   null for the action as a whole, or {"type", "id", "tenant", "relations": {"owner": ["<uuid>"]}}
#
# Both rules below read data.json, so they object to nothing until it lists something. Change the data, or this file,
# while OPA runs: it reloads them, and Kibootia's next request sees the change.
package kibootia.guardrails

# A change freeze: the listed actions are refused to everyone while they are listed.
deny contains sprintf("%s is frozen", [input.action]) if {
	input.action in data.guardrails_config.frozen_actions
}

# A blocklist: the listed principals may do none of the actions Kibootia asks about.
deny contains "the principal is blocked" if {
	input.principal.id in data.guardrails_config.blocked_principals
}
