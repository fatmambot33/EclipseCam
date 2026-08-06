# Android instrumentation CI gate

EclipseCam executes its Android instrumentation suite in a dedicated GitHub Actions job named `Android instrumentation / emulator-instrumentation`.

## Pinned emulator

- runner: `ubuntu-24.04`
- API level: 35
- system image: `google_apis`
- architecture: `x86_64`
- hardware profile: `pixel_7`
- Java: Temurin 17
- Gradle: 8.10.2
- emulator runner: `reactivecircus/android-emulator-runner@v2.34.0`

The job enables KVM, disables animations, starts a headless emulator, and runs:

```shell
gradle --no-daemon connectedDebugAndroidTest
```

Product-test failures are returned directly and are not retried. Repository maintainers may rerun a job only when the logs show an infrastructure failure before or outside test execution.

## Evidence

Every run uploads the connected-test reports, raw Android test results, and logcat. Failed runs also capture the emulator screen plus activity and notification state.

The existing `Android CI / validate` job remains responsible for JVM tests, lint, debug APK assembly, and instrumentation APK assembly. This emulator job is independent of signing and Play publishing credentials and can be configured as a required branch-protection check.

## Deliberate-failure verification

To prove the gate blocks a merge, create a temporary branch with one deterministic failing instrumentation assertion, open a pull request, and verify that `Android instrumentation / emulator-instrumentation` is red. Revert the temporary assertion rather than weakening or retrying the product failure. Keep the failed workflow run as the audit record.

Physical Pixel 7 Pro, screen-off, intended-duration, thermal, battery, storage, interruption, and all-rear-lens validation remain separate release gates.
