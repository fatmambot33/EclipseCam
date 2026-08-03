# EclipseCam delivery pipeline

This document is the operating manual for building, testing, publishing, and updating EclipseCam.

`PRODUCT.md` defines what the product must become. This document defines how changes reach users safely.

## Delivery model

```text
feature branch
    ↓
pull request
    ↓
Android CI: tests + lint + debug APK
    ↓
merge to main
    ↓
manual Google Play publish workflow
    ↓
internal testing
    ↓
closed/open testing when ready
    ↓
protected production rollout
```

## Workflows

### Android CI

File: `.github/workflows/android.yml`

Runs on every pull request and every push to `main`.

It:

- installs Java and Android SDK 36
- runs unit tests
- runs Android lint
- builds a debug APK
- uploads the APK and lint report as GitHub artifacts

Use the debug APK for rapid testing on the Pixel 7 Pro.

### Publish to Google Play

File: `.github/workflows/publish-play.yml`

Runs only when manually started from GitHub Actions.

It:

- assigns a unique increasing Play `versionCode`
- accepts a human-readable `versionName`
- restores the encrypted upload key
- validates required secrets
- runs tests and release lint
- builds a signed Android App Bundle
- stores the AAB and R8 mapping file as GitHub artifacts
- publishes the bundle to the selected Play track

Supported tracks:

- `internal`
- `alpha`
- `beta`
- `production`

Production can use a staged rollout fraction such as `0.1` for 10 percent.

## One-time GitHub configuration

Create these repository or environment secrets under:

`Repository → Settings → Secrets and variables → Actions`

```text
MAPS_API_KEY
ECLIPSE_CAM_KEYSTORE_BASE64
ECLIPSE_CAM_STORE_PASSWORD
ECLIPSE_CAM_KEY_ALIAS
ECLIPSE_CAM_KEY_PASSWORD
PLAY_SERVICE_ACCOUNT_JSON
```

Recommended alias:

```text
upload
```

Never commit any of these values.

## Google Play API service account

One-time setup:

1. Enable the Google Play Android Developer API in a Google Cloud project.
2. Create a dedicated service account, for example `eclipsecam-play-publisher`.
3. Create a JSON key for that account.
4. In Play Console, open **Users and permissions**.
5. Invite the service-account email.
6. Give it access only to EclipseCam.
7. Grant the minimum permissions required to create and manage releases.
8. Store the complete JSON file content in the GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`.

The package already exists in Play Console as:

```text
com.fatmambo33.eclipsecam
```

The first AAB may need to be uploaded manually in Play Console before API publishing is accepted. After that, GitHub Actions can publish subsequent releases.

## GitHub environments

Create two environments:

### `google-play-testing`

Used for internal, alpha, and beta releases.

Store deployment secrets here when supported by the repository plan. It may run without manual approval.

### `google-play-production`

Used only for production.

Configure:

- required manual approval
- only `main` may deploy
- production Play credentials and signing secrets

This prevents an accidental production release.

## Versioning

The workflow generates an increasing `versionCode` from the GitHub run number.

The person triggering the release provides `versionName`, for example:

```text
1.0.0-alpha02
1.0.0-beta01
1.0.0
1.1.0
```

Never reuse a Play `versionCode`.

## Everyday development loop

1. Create a branch named `feature/<description>` or `fix/<description>`.
2. Make one coherent change.
3. Open a pull request to `main`.
4. Wait for Android CI.
5. Download and install the debug APK on the Pixel 7 Pro.
6. Test the feature physically.
7. Compare the change with `PRODUCT.md`.
8. Merge only when tests, lint, and physical validation pass.

## Publishing an internal update from the phone

In the GitHub mobile site or app:

1. Open **Actions**.
2. Select **Publish to Google Play**.
3. Tap **Run workflow**.
4. Select track `internal`.
5. Use status `completed`.
6. Enter a new version name.
7. Run the workflow.

After the job succeeds, Play Console processes the release and delivers it to internal testers.

## Production release policy

Do not publish directly from an untested commit.

Before production:

- scientific validation gates in `PRODUCT.md` pass
- the same build has been tested through an earlier Play track
- Pixel 7 Pro camera, GPS, orientation, storage, battery, and thermal tests pass
- privacy policy and Data Safety answers match the shipped code
- screenshots and listing text match actual features
- release notes are updated
- production environment approval is granted

Prefer staged production rollout:

```text
0.1 → 0.25 → 0.5 → 1.0
```

Pause the rollout if Play Console reports crashes, ANRs, or severe user-impacting failures.

## Updating release notes

Before publishing, edit:

```text
fastlane/metadata/android/en-US/changelogs/whatsnew-en-US
```

Keep notes factual and limited to capabilities present in the uploaded build.

## Recovery

If CI fails:

1. Open the failed workflow.
2. Identify the first failing step.
3. Fix the source or workflow in a branch.
4. Open a pull request.
5. Let CI re-run.

If Play publishing fails:

- confirm the service account is invited to the Play developer account
- confirm it has EclipseCam app permissions
- confirm the Android Publisher API is enabled
- confirm the package has had an initial manual upload if required
- confirm all Play Console setup tasks for the target track are complete
- confirm the new version code exceeds every previously uploaded version code

## Security

- Keep the repository free of keystores, API keys, JSON credentials, and passwords.
- Restrict the Maps key to package `com.fatmambo33.eclipsecam`, the correct signing SHA-1 certificates, and Maps SDK for Android only.
- Restrict the Play service account to EclipseCam and release management only.
- Use a protected GitHub production environment.
- Keep permanent offline backups of the upload keystore and passwords.
