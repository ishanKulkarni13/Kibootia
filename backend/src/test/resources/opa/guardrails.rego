# The guardrail policy the tests run against (ADR 0066). It can only give reasons to refuse.
package kibootia.guardrails

# Creating widgets is frozen while data.frozen is true.
deny contains "widgets are frozen" if {
	input.action == "widgets.widgets.create"
	data.frozen == true
}

# A principal listed in data.blocked may do none of the actions OPA is asked about.
deny contains "the principal is blocked" if {
	input.principal.id in data.blocked
}
