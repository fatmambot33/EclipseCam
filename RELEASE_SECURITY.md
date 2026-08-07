# Release artifact and signing security

EclipseCam separates repository-verifiable release quality from credential-backed Google Play publication.

## Credential-free verification

`.github/workflows/release-verification.yml` runs on pull requests and `main` without production credentials. It:

- scans every tracked file for signing/credential filenames and high-confidence secret signatures;
- reruns the runtime privacy audit;
- runs JVM tests and release lint;
- builds a minified release Android App Bundle without a signing key;
- verifies that the AAB contains the base Android manifest and no JAR signature entries;
- records the package identifier, SDK levels, manifest permissions, artifact size, and SHA-256 digest;
- archives the unsigned AAB, R8 mapping, lint output, AAB contents, merged-manifest evidence when available, and machine-readable metadata.

The unsigned artifact proves that release configuration can be built and inspected without granting CI jobs access to the upload key. It is **not publishable to Google Play** and must never be confused with the credential-backed artifact created by the publishing workflow.

## Source-control protections

`.gitignore` excludes common Android signing files, private keys, local environment files, service-account JSON files, and build output. The release verifier independently checks `git ls-files`, so a previously tracked forbidden file fails CI even if an ignore rule is later added.

The source scan rejects:

- Java/Android keystores and common certificate/private-key formats;
- `keystore.properties`, `signing.properties`, `google-services.json`, and service-account/credential JSON filenames;
- PEM private-key material;
- Google API keys, GitHub token formats, AWS access-key formats; and
- Google service-account JSON that includes private-key material.

Workflow references such as `${{ secrets.ECLIPSE_CAM_STORE_PASSWORD }}` are configuration names rather than secret values and are intentionally permitted.

## Credential-backed Play publication

`.github/workflows/publish-play.yml` is the only release path intended to restore the upload keystore and publish to Google Play. Its secure inputs are:

| Secret | Purpose |
| --- | --- |
| `ECLIPSE_CAM_KEYSTORE_BASE64` | Base64-encoded Android upload keystore restored only in runner temporary storage |
| `ECLIPSE_CAM_STORE_PASSWORD` | Upload keystore password |
| `ECLIPSE_CAM_KEY_ALIAS` | Upload-key alias |
| `ECLIPSE_CAM_KEY_PASSWORD` | Upload-key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play Android Developer API service-account credential |

`MAPS_API_KEY`, if configured for development or a future map feature, is separate from release signing and must remain platform/package/API restricted. No unrestricted key belongs in source control.

The publish workflow removes its temporary keystore after the build. Production publication additionally uses the protected `google-play-production` GitHub environment and should require human approval.

## Release evidence and gate status

A green Release Verification workflow satisfies the repository-side, credential-free evidence required by issue #91. It does **not** satisfy PRODUCT.md release gate 11 by itself. That gate requires the exact uploaded, signed AAB to pass signing checks and Google Play pre-launch validation.

Before describing a release as production-ready, the account owner must still verify the signed artifact and Play Console evidence for the exact version being promoted.
