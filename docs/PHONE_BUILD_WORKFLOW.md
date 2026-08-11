# Phone-only signed Play bundle workflow

EclipseCam separates **building a signed Android App Bundle** from **publishing it to Google Play**.

This is intentional for the first Play upload: Google Play App Signing is already enabled, but the upload certificate is not registered until the first bundle signed with EclipseCam's upload key is uploaded.

## Security boundary

Never commit a keystore, private key, password, service-account JSON file, or their base64 contents.

The repository only reads signing material from GitHub Actions secrets. Google Play keeps the app-signing key. EclipseCam uses a separate upload key only to authenticate bundles sent to Play.

## Required signing secrets

Configure these repository or `google-play-testing` environment secrets before running the workflow:

- `ECLIPSE_CAM_KEYSTORE_BASE64` — base64 encoding of the dedicated upload keystore
- `ECLIPSE_CAM_STORE_PASSWORD` — upload-keystore password
- `ECLIPSE_CAM_KEY_ALIAS` — alias of the upload key
- `ECLIPSE_CAM_KEY_PASSWORD` — upload-key password

`PLAY_SERVICE_ACCOUNT_JSON` is **not required to build the first signed bundle**. It is required only when `publish_to_play` is enabled or the release-request push path intentionally publishes through the Google Play API.

## Create the upload key

Generate the upload key only in a trusted local environment with Java `keytool`. Keep the resulting keystore backed up outside the repository.

Example:

```bash
keytool -genkeypair \
  -v \
  -keystore eclipsecam-upload.jks \
  -alias eclipsecam-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Encode the keystore for the GitHub secret without printing it into shell history or committing the output:

```bash
base64 < eclipsecam-upload.jks | tr -d '\n' > eclipsecam-upload.jks.base64
```

Copy the contents into `ECLIPSE_CAM_KEYSTORE_BASE64`, then securely delete the temporary `.base64` file. Preserve the original keystore in secure backup storage.

## Build the first signed bundle

From GitHub Actions, run **Publish to Google Play** manually with:

- `publish_to_play`: `false`
- `track`: `internal`
- `status`: `draft` or `completed` (ignored while build-only)
- the intended `version_name`

The build job:

1. verifies all four signing secrets are present;
2. restores the upload keystore only inside the ephemeral runner;
3. verifies the configured alias;
4. exports the **public** upload certificate and prints its fingerprints;
5. runs unit tests, release lint, and `bundleRelease`;
6. uploads the signed `.aab` and public upload certificate as separate workflow artifacts;
7. removes the restored keystore even if the job fails.

The publish job is skipped when `publish_to_play` is `false`.

## First Play Console upload

Download the signed `.aab` artifact and upload it manually to **Internal testing** in Google Play Console.

After Play accepts the first bundle, the **Upload key certificate** section in App integrity should show fingerprints matching the public certificate artifact produced by the workflow.

Do not change the Google-managed app-signing key during this process.

## Automated publishing later

After the first upload certificate is registered and the Google Play service account is configured, add `PLAY_SERVICE_ACCOUNT_JSON` as a protected GitHub secret and run the workflow with `publish_to_play: true` for the intended testing track.

Production publication remains gated by `PRODUCT.md`, including physical-device validation, privacy/Data Safety review, Play pre-launch checks, and unaided user validation.
