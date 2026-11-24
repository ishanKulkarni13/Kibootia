# Security policy

Kibootia is a starter that applications copy and own ([ADR 0023](kibootia-docs/decisions/0023-template-fork-adoption-model.md)). It has no released version yet, so there is no supported-versions table.

## Reporting a vulnerability

Please do not open a public issue for a vulnerability. Use GitHub's **Report a vulnerability** form on the repository's Security tab (private vulnerability reporting). The maintainer needs to have that feature switched on in the repository settings; until a contact address is published here, that is the only channel.

Please include what you found, how to reproduce it, and what you think the impact is. Fixes will be noted in the changelog with a security marker once releases exist. The owner has not yet set a response-time target; add one here when they do.

## What is already documented

- The [threat model](kibootia-docs/operations/security/threat-model.md) lists what is defended and how.
- [Secrets management](kibootia-docs/operations/security/secrets-management.md) and the [authorization architecture](kibootia-docs/architecture/identity/authorization.md) describe the model.
- Known weaknesses that have not been fixed are tracked in the [architecture assessment](kibootia-docs/architecture/architecture-assessment.md) and the [open questions](kibootia-docs/decisions/open-questions.md), not hidden.

## Scope

In scope: the code, build and deployment files in this repository. Out of scope: vulnerabilities in third-party software the starter uses (report those upstream), and deployments of applications built on Kibootia.
