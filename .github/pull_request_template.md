## What and why

<!-- What changes, and the problem it solves. Link the milestone, ADR or open question. -->

## Checklist

- [ ] Tests: allowed **and** denied cases for anything protected; a test that fails without the change
- [ ] Architecture: `ModularityTest` and `ArchitectureTest` pass; a new module declares its `allowedDependencies`
- [ ] Data: a Flyway migration in the owning module's folder; no query that skips `tenant_id`
- [ ] No external call (storage, Keycloak) inside a database transaction
- [ ] Docs: the document that owns the fact is updated; an ADR for a decision; the folder `README.md` if a document was added
- [ ] `implementation-status.md` says "verified" only with a CI run ID; otherwise "observed (local run; CI run pending)"
- [ ] `openapi.json` and the generated frontend client are in step (CI checks drift)
- [ ] No secret, token or personal data in code, tests, logs or audit entries

## Verification

<!-- Commands you ran, or the CI run. Say what you did not run. -->
