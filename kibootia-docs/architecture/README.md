# Architecture

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** not applicable (index). What exists, and how it was verified, is in [implementation status](../product/roadmap/implementation-status.md).

| Item | Summary |
|---|---|
| [overview.md](overview.md) | System context and the main building blocks |
| [context-and-boundaries.md](context-and-boundaries.md) | What Kibootia owns versus what a consuming application and external systems own |
| [layers-and-boundaries.md](layers-and-boundaries.md) | Layers, model separation, features and shared platform packages |
| [module-catalogue.md](module-catalogue.md) | Each module: purpose, contract, tables, events, allowed dependencies, and the runtime cycles |
| [architecture-assessment.md](architecture-assessment.md) | A dated review of data, ownership, modularity, coupling, cohesion and enterprise gaps, with recommendations |
| [architecture-enforcement.md](architecture-enforcement.md) | Spring Modulith verification and ArchUnit rules |
| [frontend.md](frontend.md) | The Next.js server as backend-for-frontend and user interface; request paths and frontend conventions |
| [audit-log.md](audit-log.md) | What the audit log records and how it handles personal data |
| [communication.md](communication.md) | Synchronous contracts, in-process events, the outbox and jobs, brokers as a recipe |
| [identity/](identity/README.md) | Authentication through the Next.js backend-for-frontend, token validation, authorization, user lifecycle |
| [data/](data/README.md) | Persistence stack, model separation, migrations, transactions, data ownership |
| [api/](api/README.md) | API-wide conventions |

Decisions behind these documents are in the [ADR index](../decisions/INDEX.md). Diagrams are inline Mermaid.
