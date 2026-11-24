# Enable Open Policy Agent guardrails

- **Type:** recipe
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** verified (`OpaGuardrailIT` against a real OPA 1.22.2, `OpaUnreachableIT`, `PolicyConfigurationTest`, and the policy's own `opa test`; CI run 45385703846, 2026-11-24).

## Problem

Some refusals belong to a policy owned outside the application: freeze changes during a release, block an account the security team flagged, allow erasing users only through an approved process. Kibootia's roles describe who may do what; a guardrail sits above them and can take an action away, never give one ([ADR 0066](../decisions/0066-opa-as-a-narrowing-guardrail.md)).

## When to use it, and when not to

Use it for a few actions where an outside policy must have the last word. Do not use it to express ordinary permissions: those belong in roles, where the role editor and the audit log show them. OPA is asked about the listed actions only, and refuses them while it cannot answer.

## Prerequisites and compatibility

Branch `m-policy` or later; Open Policy Agent 1.22.2 (the version in `compose.yaml`).

## Steps

1. In `.env`, add `policy` to `COMPOSE_PROFILES` and run `pnpm infra:up`. OPA listens on `127.0.0.1:8181` with the policy in `infra/opa`.
2. Set `KIBOOTIA_POLICY_OPA_ENABLED=true` and list the guarded actions, for example `KIBOOTIA_POLICY_OPA_ACTIONS=identity.users.erase,*.purge`. Running the backend on the host, also set `KIBOOTIA_POLICY_OPA_URL=http://localhost:8181`; in the `apps` profile it is set to `http://opa:8181`.
3. Restart the backend. Startup fails, naming the setting, if the address or the action list is missing.
4. Write the policy in `infra/opa/guardrails.rego`: rules that add reasons to the set `deny`. The shipped rules refuse the actions in `data.guardrails_config.frozen_actions` and every guarded action of the principals in `blocked_principals` (`infra/opa/data.json`). Run `opa test infra/opa` (the CI job `policy` does).
5. Change data while OPA runs, for example `curl -X PUT localhost:8181/v1/data/guardrails_config/frozen_actions -d '["identity.users.erase"]'`. The next request sees it.

## Configuration and secrets

`kibootia.policy.opa.enabled`, `.url`, `.actions`, `.path` (default `kibootia/guardrails/deny`), `.connect-timeout` (default `PT0.5S`), `.read-timeout` (default `PT1S`). No secret: OPA is reached on a private network. In production, protect OPA's API (OPA supports bearer-token authentication and TLS) and do not publish its port.

## Security

- The guardrail can only narrow: it is asked after roles allowed, never for the system, and an objection always refuses.
- It fails closed for the guarded actions: an unreachable, slow or misconfigured OPA refuses them, logged as `authorization.evaluation_error`. Watch that reason in the metric `kibootia.authorization.decisions`.
- The input holds identifiers only, no names or attribute values.
- Anyone who can change the policy or its data can stop the guarded actions; treat OPA's API as an administrative interface.

## Removal

Set `KIBOOTIA_POLICY_OPA_ENABLED=false` (the default) to switch it off. To remove it, delete `backend/src/main/java/com/kibootia/backend/policy`, its tests under `backend/src/test/java/com/kibootia/backend/policy`, the `opa` service and the `KIBOOTIA_POLICY_OPA_*` lines of `compose.yaml` and `.env.example`, `infra/opa`, the CI job `policy` in `.github/workflows/ci.yml`, the `kibootia.policy.opa` lines of `application.properties`, and `policy` from `REMOVABLE_MODULES` in `ArchitectureTest` and from `ModularityTest`. Nothing else depends on the module (architecture rule 5).
