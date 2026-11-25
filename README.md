# Kibootia

A reusable, enterprise-oriented application starter: a **Spring Boot** modular monolith (backend) and a **Next.js** backend-for-frontend, with **Keycloak** for identity, **MySQL** for data and an **S3-compatible** store for files. An application copies the repository, adds its own business modules beside the foundation ones, and owns all the code ([ADR 0023](kibootia-docs/decisions/0023-template-fork-adoption-model.md)).

It is not a business application. Nothing specific to one domain belongs in the foundation modules.

## What is in it

| Module | What it does |
|---|---|
| `platform` | Technical kernel: security, errors, tenancy, persistence base types, durable events, scheduled jobs |
| `identity` | Profiles, `/me`, user administration through Keycloak, avatars |
| `access` | Roles made of grants with a reach, assignments, the authorization decision point |
| `audit` | Append-only audit log and its viewer |
| `files` | Object storage, upload checks, image processing (removable) |
| `notes` | A small example feature to copy, and to delete (removable) |
| `policy` | An optional Open Policy Agent guardrail that can only narrow what roles allow (removable, off by default) |

The architecture is checked by tests (Spring Modulith and ArchUnit), not only described. What works today, with the CI run that proves it, is in [implementation status](kibootia-docs/product/roadmap/implementation-status.md). A dated review of the structure, with recommendations, is the [architecture assessment](kibootia-docs/architecture/architecture-assessment.md).

## Quick start

Needs Docker, a Java 25 JDK (Temurin), Node 24 (see `.nvmrc`; CI and the images use it) and pnpm 11. Full steps, addresses and troubleshooting: [local setup](kibootia-docs/engineering/getting-started/local-setup.md).

```sh
pnpm install
pnpm bootstrap     # creates .env with generated secrets
pnpm infra:up      # MySQL, Keycloak, mail catcher, observability
pnpm dev           # backend and web app on the host
```

Open <http://localhost:3470>. Local demo accounts apply to development only.

Tests: `./mvnw verify` in `backend/` (needs Docker); `pnpm --filter kibootia-frontend test`.

## Where things are

| Path | Contents |
|---|---|
| `backend/` | The Spring Boot application |
| `frontend/` | The Next.js application and BFF |
| `infra/keycloak/` | The Keycloak realm as OpenTofu code |
| `compose.yaml`, `scripts/` | The local stack and its setup scripts |
| `kibootia-docs/` | All documentation: [start here](kibootia-docs/README.md) |

## Status and licence

Milestones 0 to 5, 7 and 8 are built and verified in CI, with part of the architecture hardening of milestone 5.1 and an optional Open Policy Agent guardrail; production readiness and the first release are not. Kibootia is licensed under the [Apache License, Version 2.0](LICENSE) ([ADR 0068](kibootia-docs/decisions/0068-apache-2-0-licence.md)); the changes of each version are in the [changelog](CHANGELOG.md). [Implementation status](kibootia-docs/product/roadmap/implementation-status.md) is the single reference.

Security reports: see [SECURITY.md](SECURITY.md).
