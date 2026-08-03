# Contributing to EclipseCam

EclipseCam is built against the product contract in `PRODUCT.md` and the delivery program in GitHub issue #1.

## Agent workflow

1. Pick one open GitHub issue with no active assignee.
2. Read `PRODUCT.md`, `PIPELINE.md`, and the issue dependencies.
3. Work on a dedicated branch named `issue-<number>-short-description`.
4. Keep the change limited to the issue acceptance criteria.
5. Add or update tests for all deterministic logic.
6. Run the relevant Gradle tests and lint.
7. Open a pull request referencing `Closes #<number>`.
8. Include validation evidence, known limitations, and physical-device testing still required.

## Parallel-work rules

- Do not modify another issue's files without documenting the dependency.
- Prefer small domain modules and stable interfaces over broad rewrites.
- Astronomy logic must remain deterministic and offline.
- Google Maps may only be used as an optional basemap.
- Never commit API keys, keystores, service-account JSON, user location, or photos.
- Do not claim scientific or production readiness without passing the gates in `PRODUCT.md`.
