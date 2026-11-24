# Add a feature: copy Notes

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-24

> **Implementation:** verified for the backend and the frontend unit tests (CI run 42959703043, 2026-11-11). The notes end-to-end story passed in CI run 45354022559 (2026-11-24), so the browser path of the example is verified too ([verification standard](../testing/verification-standard.md)).

## Goal

A new business feature in your application that behaves like the rest of Kibootia: CRUD with validation, a paged and filtered list, ownership rules enforced by the decision point, tenant isolation, soft delete, an optional file, a durable event, its own migration, a screen, and tests at every level. The reference is the example feature, **Notes** (working name kept, [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md)). It is small on purpose and meant to be copied, then removed ([remove notes](../../recipes/remove-notes.md)).

## Prerequisites

The application runs ([start and check](../getting-started/start-and-check.md)) and you have read the [package structure](../conventions/package-structure.md). In what follows, replace `notes` and `note` with your feature's name; the example is a *resource owned by one user*, which is the commonest case.

## Steps

### 1. Backend: the module

Create `com.kibootia.backend.<feature>` with the layers of `notes/`: `application` (use cases and ports), `persistence` (JPA), `web` (controller and DTOs), and at the top level only what other modules may use (events, permission names). Add the module declaration (`package-info.java` with `@ApplicationModule`). Other modules reach yours only through its top-level package.

A feature module is **default-on and removable** ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)): nothing outside it may depend on it. Add its name to `REMOVABLE_MODULES` in `ArchitectureTest` so rule 5 checks that.

### 2. Permissions and who may do what

Declare actions and resource types in a `PermissionProvider` bean (`NotesPermissions`, [ADR 0065](../../decisions/0065-milestone-8-implementation-decisions.md)). Name actions `<module>.<resource>.<action>` and types `<module>.<resource>`. For records owned by a user, declare the type with the `owner` relation (`ResourceType.of(NOTE, "A note", ResourceRef.OWNER)`) and the actions on those records with `Permission.onRecord(VIEW, NOTE, ...)`: a role can then grant each either for every record or for the holder's own. Actions decided without a record (create, restore, purge, anything administrative) are `Permission.onCollection(...)`. Offer an action to every user with `.forEveryUser()`, or `.forEveryUser(Reach.owner())` for their own records only; the rest are granted through roles.

In the use cases (`NoteService`):

- a check that needs no record: `@RequiresPermission(NotesPermissions.CREATE)`;
- the record implements `Resource` and describes itself with a `ResourceRef`: its type (declare a constant, `NotesPermissions.NOTE = "notes.note"`), its ID, its tenant and its owner (`ResourceRef.of(...).ownedBy(ownerId)`; see `Note`);
- the read that loads a record: `authorization.requireVisible(NotesPermissions.VIEW, notes.find(id), NoteService::notFound)`. A caller with no view grant at all gets `403`, decided before the record is read; a record out of their reach and a missing one both get your `404` ([ADR 0064](../../decisions/0064-milestone-7-implementation-decisions.md));
- a check on a loaded record: `authorization.require(NotesPermissions.UPDATE, note)`, so one they may see but not change is `403`;
- a list asks `authorization.requireFilter(...)` and turns the access filter into its query, so paging stays correct. A store that understands only ownership calls `filter.ownerOnly()`, which refuses any filter shape it does not know;
- a rule grants cannot express (a locked record) is a `ResourceRule` bean. It can only narrow what grants allow;
- a permission whose every use should be reviewable (purging, erasing) is declared `.asSensitive()`, so its allowed uses are written as decision records too.

Every public method of a use-case class needs one of these or `@NoAuthorization("reason")`; ArchUnit rule 7 fails the build otherwise.

### 3. Persistence: the store port and the entity

The use cases depend on a port (`NoteStore`), never on JPA. `JpaNoteStore` implements it over `NoteEntity`, which extends `SoftDeletableEntity` (key, tenant, version, audit columns and soft delete come with it). Map by hand between entity and model (`NoteEntityMapper`). Entity queries are filtered by tenant and skip deleted rows on their own; a query that must reach deleted rows (restore, purge, the deleted list) is native and **names the tenant itself**.

Put a unique key on what must be unique per owner, including the generated active-marker column the base type needs (see the Notes migration), and map the duplicate to a `409` with a code of your own.

### 4. Migrations

One folder per module, `src/main/resources/db/migration/<module>/`, files named `V<yyyyMMddHHmm>__<module>_<what>.sql`. Add the folder to `spring.flyway.locations` in `application.properties` and in the test `application.properties`. `BackendApplicationIT` migrates from empty and Hibernate validates the mapping against the result.

### 5. The API

`NotesController` shows the conventions of [ADR 0043](../../decisions/0043-http-api-conventions.md): plural kebab-case under `/api/v1`, DTOs (never entities) with `@Schema(requiredMode = REQUIRED)` on required fields, validation annotations, `201` with `Location`, `PageResponse` for lists with an allow-list of sort fields, named filter parameters, a stale `version` as `409`. An action that is not plain CRUD is a sub-resource (`POST /notes/{id}/restore`, `PUT /notes/{id}/attachment`).

### 6. A file (optional)

Declare a `FileCategory` (`NoteAttachmentService.CATEGORY`: a name, a size limit and the renditions), get `StoredFiles` through an `ObjectProvider` so the feature works with the files module off, authorize first, then call `store`, and keep the returned ID in your own table with no foreign key. Spend the upload allowance (`RateLimitCategory.UPLOADS`) *after* the permission check. Do not call storage inside a database transaction: store first, then switch the reference in a short transaction, then discard the old file.

Decide what a delete does to the file. Notes keeps it hidden with the note, so a restore brings it back, and removes it when the note is purged. The files module erases every file a user *owns* when the user is erased, so give the file to the record's owner, not to whoever uploaded it.

### 7. Events

Publish a `DomainEvent` (a record with an `EventContext`) from the use case, in its transaction, and handle it with `@DurableEventListener` in a non-public method. `NoteDeleted`, `NotePurged` and the handlers in `NoteEvents` show it. React to `UserErased` to remove what you hold for a user.

### 8. Contract and client

Run `./mvnw verify` in `backend/`: `OpenApiContractIT` rewrites `backend/openapi.json`; commit it. Then `pnpm --filter kibootia-frontend api:generate` and commit `schema.gen.ts`. CI fails if either is stale.

### 9. Frontend

One folder under `frontend/src/features/<feature>/` with `api.ts` (TanStack Query hooks), the screens, and pages under `src/app/<feature>/` that only compose a screen inside `PageShell`. Add the feature to `frontend/src/modules.ts` (inside its own `module:<name>:start` and `:end` lines) and its paths to the `matcher` in `src/proxy.ts` (a line tagged `module:<name>`). The patterns to copy:

- list: `NotesScreen` (search, sort, paging, a view that depends on permissions);
- form: `NoteEditor` (zod, `applyProblem`, the version in the key, `mutateAsync`);
- file: `NoteAttachment` (the same file check as the avatar, a private `<img>`);
- permission gating: `usePermissions()` and `<Can permission="...">`, for usability only.

### 10. Tests

One layer each, all in the Notes module as examples:

- logic: plain unit tests (`NoteAttachmentServiceTest`, the mapper tests);
- API through the real stack: `NotesApiIT` and `NoteAttachmentApiIT`, with an allowed and a denied case for every operation, list scopes, tenant isolation, soft delete and purge;
- a limit or a module switch that needs its own configuration: `NoteAttachmentRateLimitIT`, `NoteAttachmentFilesDisabledIT`;
- the contract: `NotesOpenApiIT`;
- screens: Vitest with `stubApi`, `renderScreen` and `meWith` (`notes-screen.test.tsx` and the others);
- the whole path in a browser, with an accessibility check on every state: `frontend/tests/e2e/notes.spec.ts`.

Keep the tests of other modules free of your feature's names: use `WidgetFixtures` for a stand-in, so your feature can be removed.

## Verifying the result

`./mvnw verify` in `backend/` passes, and so do the frontend scripts `lint`, `typecheck`, `test` and `build` (each run as `pnpm --filter kibootia-frontend <script>`); `git status` shows no change to `openapi.json` or `schema.gen.ts` after them. The end-to-end story needs the full stack and runs in CI on pull requests labelled `e2e`.

## Troubleshooting

- *Startup fails with "names a permission nobody declared"*: the `PermissionProvider` is not a bean, or the name in `@RequiresPermission` has a typo.
- *A native query returns another tenant's rows*: it does not name `tenant_id`.
- *The IDE shows compile errors Maven does not, or tests fail with "Unresolved compilation problems"*: the IDE's Java language server compiled into `backend/target` with an older source level; build from a clean copy or with the IDE's Java build switched off.
- *A cached picture does not change*: the address must carry a version that changes with the file.

## Related documents

[Package structure](../conventions/package-structure.md), [layers and boundaries](../../architecture/layers-and-boundaries.md), [architecture enforcement](../../architecture/architecture-enforcement.md), [API conventions](../../architecture/api/conventions.md), [definition of done](../testing/verification-standard.md#definition-of-done-for-a-feature), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md), [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md), [remove notes](../../recipes/remove-notes.md).
