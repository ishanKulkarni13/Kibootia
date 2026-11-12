# Milestone 5: Example Feature

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** built; verified except for the end-to-end tests. The backend, frontend, image build and scan, secret scan and all four rows of the `module-removal` job passed in CI on pull request #7 (run 42959703043, 2026-11-11) and on `main` after the merge (run 42981706613). The end-to-end job was **skipped** in both runs, so the 31 end-to-end tests (9 of them the new notes story) have not run in CI and the Playwright criterion below is open (OQ-86 in [open questions](../../decisions/open-questions.md)). Status checked with the GitHub CLI on 2026-11-11. Decisions made while building: [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md).

## Scope

One small, generic, deletable example feature, **Notes** (the name stays, OQ-37 closed), that shows applications how to add their own: CRUD, validation, pagination and filtering, ownership rules and list scopes through the decision point, tenant isolation, a file attachment, an event, its own migrations and tests at every level. It also completes the module-removal test matrix. Decision: [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), extended by [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md).

## Completion criteria (approved by the owner on 2026-11-08 with the other milestones, OQ-49; the choices of 2026-11-11 are in ADR 0052)

- The example feature demonstrates each pattern above in backend and frontend, meets the [definition of done](../../engineering/testing/verification-standard.md#definition-of-done-for-a-feature), and its documentation explains how to copy the pattern.
- With several modules in place, the Spring Modulith and ArchUnit rules about modules, cycles and authorization checks are meaningful and pass ([architecture enforcement](../../architecture/architecture-enforcement.md)).
- A removal recipe exists for the example feature and for each optional module. CI builds and starts the application with each of them removed or disabled.
- A Playwright flow covers sign-in, administration, avatar and the example feature, including denied cases and an automated accessibility check.
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).

## Status against the criteria

Level: **verified for every criterion except the Playwright flow** (CI runs 42959703043 and 42981706613, 2026-11-11). The evidence column below was written from the local run before CI ran and is kept as written; the last row records what CI has since shown.

| Criterion | Evidence |
|---|---|
| Each pattern, backend and frontend | **CRUD, validation:** `NotesApiIT` (35 cases), `NotesController`, `note-editor.test.tsx`. **Pagination, filtering, sorting:** `NotesApiIT` (paging, allow-listed sort, text filter with escaped wildcards, owner filter), `notes-screen.test.tsx`, the end-to-end story (21 notes, two pages, sorted). **Ownership and list scopes through the decision point:** `NotesPermissions` (`-any` pairs), `NotesApiIT` (own, any, 404 versus 403, empty page for another owner), the deleted view for `notes.notes.restore` only. **Tenant isolation:** `NotesApiIT`, `NoteAttachmentApiIT`. **File attachment:** `NoteAttachmentService`, `NoteAttachmentApiIT` (16 cases: kept in proportion, replace, remove, 415, 413, 400, visibility, tenant, deleted and restored, purge, erase), `NoteAttachmentRateLimitIT` (429), `NoteAttachmentFilesDisabledIT`, `ImageProcessorTest` (fit renditions), `note-attachment.test.tsx`. **Event:** `NoteDeleted`, `NotePurged` (durable, with the purge), `NoteEvents`. **Own migrations:** `db/migration/notes` (two files), migrate-from-empty in `BackendApplicationIT`. **Tests at every level:** unit, store and mapper, API through the real stack, contract (`NotesOpenApiIT`, `openapi.json`), screens (Vitest), browser (`notes.spec.ts`). **Documentation:** [how to add a feature](../../engineering/guides/add-a-feature.md) |
| Modulith and ArchUnit rules are meaningful and pass | Six modules with code. `ModularityTest` (4 tests: the core modules exist, every module is known, `verify()` passes, the core modules depend on each other and `platform` on none). `ArchitectureTest` (10 tests, rule 5 for `files` and `notes`). `ArchitectureRulesFireTest` (8 tests): rules 5, 7, 9, 10 and 11 fail on classes made to break them and pass on classes made to satisfy them. Modulith itself cannot be given a broken fixture (it ignores test classes), which the ADR records |
| A removal recipe per module; CI builds and starts the application with each removed or off | [Recipes](../../recipes/README.md): [remove notes](../../recipes/remove-notes.md), [disable files](../../recipes/disable-files.md), [disable observability](../../recipes/disable-observability.md). `scripts/remove-module.mjs` and the `module-removal` job (four rows). **Run locally:** on a clean checkout the script removed 53 files and the tagged lines and passed its own leftover scan; the backend then built and passed 115 fast and 209 integration tests, and the frontend client was regenerated from the rewritten `openapi.json` and lint, type check and 171 unit tests passed (the production build was not run on that checkout). The `startup` group (4 tests) passed with its default switches. **Not run locally:** the group with each switch set through the environment (the other three rows) and the production build of the stripped frontend, because Docker stopped during the work and the build refuses a linked `node_modules` |
| A Playwright flow covers sign-in, administration, avatar and notes, with denied cases and axe | 31 tests in six specs: `sign-in`, `administration`, `registration`, `access-denied`, `avatar` and the new `notes` (9 tests: write, change, search, sort and page; a picture kept in proportion; denied cases in the page and in the API; an administrator deletes and restores with the picture). Every spec runs axe on each state. **Written and type-checked, not run:** they need the Compose stack, and starting it here would clash with the developer's running containers (see the deviations) |
| All of the above pass in CI | **Passed, except the Playwright flow.** Pull request #7 (run 42959703043) and `main` (run 42981706613): backend build and test, frontend lint, type check, test and build, image build and scan, secret scan, and the `module-removal` rows "without notes", "without files", "without observability" and "all" each concluded with success. The end-to-end job was skipped in both runs: it runs only nightly, on manual dispatch, or on a pull request labelled `e2e` |

## Deviations and limits

- **The end-to-end story has not run at all, locally or in CI.** The local stack cannot be started without stopping the developer's containers, which was not done ([conventions](../../engineering/getting-started/start-and-check.md)), and the CI job was skipped on the pull request and on `main`. The story is the part most likely to need a fix once it runs (OQ-86).
- **One test was fixed after the full run, and a second full run did not happen.** `NoteAttachmentRateLimitIT` shared a Spring context, and so a limiter, with `AvatarRateLimitIT` and failed when both ran; it now has a context of its own and passed together with the avatar one. Docker stopped afterwards, so the whole suite was not run again.
- **Modulith cannot be fed a broken fixture**, because it analyses only main classes. The cycle and access rules are shown to be non-vacuous by the real module graph instead ([ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md)).
- **One picture per note, images only.** A list of attachments is a child table and a documents category needs type rules of its own; both are left to a fork.
- **Notes show the owner as a short ID,** not a name: the note response carries no profile data.
- **`AdminApiTest` does not clear the note table** (the planned alternative was to truncate every table); the test that creates notes clears it.
- **Auditing:** deleting and restoring a note are audited; attaching or removing a picture is not.
- **The removal script is tested for `notes` only,** the one module whose code can be deleted. `files` and the telemetry export are switched off, not deleted.
- **The IDE's Java language server overwrites class files in `backend/target`** when it compiles with an older source level, which fails unrelated tests; local runs used a copy outside the workspace (see [ADR 0052](../../decisions/0052-milestone-5-implementation-decisions.md)).
