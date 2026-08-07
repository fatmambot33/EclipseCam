# Android instrumentation CI gate

EclipseCam executes its Android instrumentation suite in a dedicated GitHub Actions job named `Android CI / emulator-instrumentation`.

## Pinned emulator

- runner: `ubuntu-24.04`
- API level: 35
- system image: `google_apis`
- architecture: `x86_64`
- hardware profile: `pixel_7`
- rear camera: emulator `virtualscene`
- emulator metrics collection: disabled
- Java: Temurin 17
- Gradle: 8.10.2
- emulator runner: `reactivecircus/android-emulator-runner@v2.34.0`

The job enables KVM, disables animations, starts a headless emulator with a virtual rear camera, and invokes one wrapper command inside the emulator lifetime:

```shell
bash .github/scripts/run-android-instrumentation.sh
```

The wrapper runs `gradle --no-daemon connectedDebugAndroidTest`, preserves its exit status, captures logcat, and adds screen/activity/notification evidence after a failure. Keeping the logic in one shell process avoids losing status or breaking conditional evidence collection when the emulator runner executes commands.

Product-test failures are returned directly and are not retried. Repository maintainers may rerun a job only when the logs show an infrastructure failure before or outside test execution.

## Evidence

Every run uploads the connected-test reports, raw Android test results, and logcat. Failed runs also capture the emulator screen plus activity and notification state.

The existing `Android CI / validate` job remains responsible for JVM tests, lint, debug APK assembly, and instrumentation APK assembly. This emulator job is independent of signing and Play publishing credentials and can be configured as a required branch-protection check.

The emulator virtual camera verifies repository-side CameraX binding behavior. It does not replace the physical Pixel 7 Pro all-rear-lens matrix.

## Deliberate-failure verification

To prove the gate blocks a merge, create a temporary branch with one deterministic failing instrumentation assertion, open a pull request, and verify that `Android CI / emulator-instrumentation` is red. Revert the temporary assertion rather than weakening or retrying the product failure. Keep the failed workflow run as the audit record.

Physical Pixel 7 Pro, screen-off, intended-duration, thermal, battery, storage, interruption, and all-rear-lens validation remain separate release gates.
