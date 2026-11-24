# 0061 CI quality and supply-chain gates

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

> **Implementation:** items 1 to 7 built on branch `m-5-1-ci` (observed; CI run pending), except that image signing waits for milestone 6, the frontend coverage floor waits for two milestones of data, and item 8 is not adopted (see Status). Observed in `.github/workflows/ci.yml` and `codeql.yml`.

## Context

CI is the project's verification standard ([ADR 0018](0018-ci-as-verification-standard.md)), and what it checks is strong on behaviour and architecture. It checks little of what an enterprise reviewer asks about first (Q-1, Q-2, Q-5 of the [assessment](../architecture/architecture-assessment.md)):

- There is no coverage measurement for Java or TypeScript, no formatter or static analysis for Java.
- There is no code scanning (SAST), no dependency audit, no software bill of materials, no image signing or provenance.
- Actions are pinned by tag; one is pinned by commit.
- Four jobs have no timeout; adding any label to a pull request re-runs the whole matrix; `cancel-in-progress` also cancels runs on `main`.
- No check validates the OpenTofu files or the Compose file.

## Decision

Adopt the gates in stages, so that each is first a report and becomes a gate only when its baseline is known.

1. **Coverage.** JaCoCo for the backend and Vitest coverage for the frontend, uploaded as artifacts and summarised on the pull request. No threshold at first. After two milestones, set a floor at the measured value for new and changed code, not a global percentage.
2. **Code scanning.** CodeQL for Java and for JavaScript/TypeScript on pull requests and weekly.
3. **Dependencies.** The GitHub dependency-review action on pull requests (fails on a known high vulnerability or a disallowed licence), `pnpm audit --prod` for the frontend, and the existing Renovate configuration kept.
4. **SBOM and provenance.** The image scan already uses Trivy; add its CycloneDX output as a build artifact for both images, so no new tool is needed. Sign images and attach provenance when the first release is published (milestone 6).
5. **Pinned actions.** Pin every action by commit SHA, with Renovate's digest pinning keeping them current.
6. **Pipeline hygiene.** A `timeout-minutes` on every job; least-privilege `permissions` per job; do not cancel runs on `main`; run the matrix on a `labeled` event only for the `e2e` label.
7. **Infrastructure checks.** `tofu fmt -check` and `tofu validate` for `infra/keycloak`, `docker compose config` for `compose.yaml`, and a Dockerfile linter.
8. **Java formatting.** Adopt the Spring Java Format plugin in validate mode, because the code already follows its style (tabs, the Spring import order); a check is cheap and stops formatting churn in reviews.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| A hard coverage threshold now | Without a baseline it is either too low to matter or blocks work; the floor on changed code is more useful |
| OWASP Dependency-Check for Java | Slow, noisy, and Trivy plus dependency review already cover known vulnerabilities |
| A separate SBOM generator per ecosystem | More tools to maintain than Trivy's output provides |
| SonarQube | A service to run for a project of this size; CodeQL is hosted |

## Consequences

- CI gets longer by the scanning jobs; they run in parallel with the existing ones.
- Pinning by SHA needs Renovate to open update pull requests, which is already installed.
- Some CodeQL findings will be false positives; the project keeps a short, reviewed suppression list, as it does for gitleaks.

accepted on the owner's delegation of 2026-11-25. Delivered in [milestone 5.1](../product/roadmap/milestone-5-1-architecture-hardening.md), except signing, which waits for milestone 6.

**Changes made while building** (2026-11-25):

- **Item 8, Spring Java Format, is not adopted.** The ADR said the code already follows the Spring style. It does not: in validate mode 285 of the main and test Java files fail, because the code is written to about 120 columns and the formatter wraps comments and code at 90. Adopting it would rewrap nearly every file, against the style the project uses and with a diff that conflicts with every branch in flight. If the owner wants a formatter, the choice is to adopt the Spring style in one commit between milestones, or to configure a formatter for the existing style.
- **A pin that was not an upstream commit was found and replaced.** The Trivy action was pinned to `a9c7b0f…`, commented as v0.36.0. That commit does not exist in `aquasecurity/trivy-action` (the GitHub API answers 404 for it), and release v0.36.0 is `ed142fd…`. GitHub still runs such a SHA when it exists somewhere in the repository's fork network, so the scan job passed while running code that could not be tied to upstream. It is replaced by the signed upstream commit of v0.36.0, and the header of `ci.yml` says how to check a new pin.
- **`pnpm audit --prod` and the dependency review are gates from the start**, because the baseline is clean (no known vulnerabilities on 2026-11-25). Coverage has no threshold.
- **A documentation check was added** (`scripts/check-docs.mjs`, the `docs` job): every relative link in a Markdown file resolves, and the decision index agrees with each ADR's own status line. This is the check ADR 0060 asks for.
- **The OpenTofu formatting** of `infra/keycloak/base/main.tf` did not pass `tofu fmt -check`; it was formatted.
- **A labelled pull request runs only the end-to-end job**, and only for the `e2e` label; other labels run nothing.

## Related

[Architecture assessment](../architecture/architecture-assessment.md) (Q-1, Q-2, Q-5), [verification standard](../engineering/testing/verification-standard.md), [ADR 0018](0018-ci-as-verification-standard.md), [ADR 0035](0035-version-adoption-policy.md).
