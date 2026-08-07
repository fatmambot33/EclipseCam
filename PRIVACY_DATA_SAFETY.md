# Runtime privacy and Data Safety evidence

This document is the repository-owned evidence for EclipseCam privacy behavior. It describes the current production source tree and is intentionally narrower than a final legal privacy policy or Google Play Console declaration.

## Runtime data-flow inventory

| Data or capability | Purpose | Processing/storage | Network behavior | User control |
| --- | --- | --- | --- | --- |
| Precise/coarse location | Observer position, eclipse-path guidance, capture/session context | Processed locally; session/export metadata may contain location when the feature records it | No EclipseCam backend exists; application source contains no direct network client for location | Android permission can be denied/revoked; export can remove JPEG GPS metadata |
| Camera | Preview and eclipse still-image capture | Captures remain in app-private/session storage until explicit export/share | No automatic photo upload | Android camera permission; explicit arming/capture flow |
| Orientation/motion sensors | Alignment, framing, stability gate | Processed in memory/local session state | None | Feature degrades when sensors are unavailable |
| Battery/thermal/storage state | Capture safety and degradation policy | Processed locally | None | Informational/safety behavior only |
| Local photos/video/montage | Gallery, timelapse, montage, export | App-private storage; explicit user-selected MediaStore/document destination or bounded share staging | Only leaves the app after an explicit Android share action chosen by the user | Export/share is explicit; location metadata can be removed |
| Capture checkpoints/plans | Process recovery and reliable automation | App-private local files | None | Cleared/finished by local session lifecycle |
| Notifications | Foreground capture status/actions | Android notification subsystem | None | Android notification permission where required |
| Internet capability | Future/optional MapLibre basemap tiles | No application-owned account, analytics, or backend traffic | The current production source defines only a validated HTTPS/asset tile-source contract and contains no concrete runtime tile endpoint or direct network client | Core astronomy, capture, media, and device-state functions are local-first |

## Manifest permissions

The audited production manifest currently declares only:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `CAMERA`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CAMERA`
- `INTERNET`

`android:allowBackup` and `android:fullBackupContent` are disabled. Cleartext network traffic is disabled. The capture service and export `FileProvider` are non-exported.

## SDK and dependency evidence

The production dependency graph intentionally contains AndroidX/Compose, CameraX, Kotlin coroutines, MapLibre Native, and AndroidX Media3. It does not intentionally include advertising, behavioral analytics, attribution, social-login, crash-upload, or user-account SDKs.

CI runs `.github/scripts/audit-privacy.py` to fail closed when:

- a manifest permission is added or removed without updating this audit;
- backup or cleartext-traffic protections are weakened;
- a service/provider becomes exported;
- a known analytics/tracking dependency marker appears;
- a direct application-owned HTTP client appears; or
- a new runtime URL literal appears outside the reviewed MapLibre tile-source contract.

The generated CI evidence is stored under `build/privacy-audit/evidence.txt` and uploaded with Android validation artifacts.

## Sharing, retention, and deletion

EclipseCam does not automatically upload media. Export and Android sharing require an explicit user action. Share staging is bounded and temporary; failed/cancelled export staging is cleaned up by the export implementation. App-private session assets remain local until the user exports them or removes application data/session content through the supported local lifecycle.

JPEG export offers a privacy choice that removes GPS EXIF metadata before the file is published or shared. Instrumentation tests verify GPS removal/preservation, MediaStore publication/readback/delete, `FileProvider` scoping, and explicit sharing/document intents.

## Data Safety declaration evidence

Based on the current production source, repository evidence supports these statements for Play review:

- no advertising SDK;
- no behavioral analytics SDK by default;
- no EclipseCam account or authentication system;
- no automatic photo or location upload;
- no EclipseCam backend receiving user data;
- location is used locally for app functionality;
- photos/videos are created and stored locally for app functionality;
- data can leave the device only through explicit user-directed export/share or a future explicitly configured online basemap request that must not include EclipseCam user payloads;
- transport is restricted to HTTPS for any future configured online tile source.

These statements are engineering evidence, not a substitute for the final Play Console form. The final privacy-policy URL, Play Data Safety answers, and store declarations must be reviewed against the exact uploaded AAB by the account owner before release.

## Release-gate status

Repository-side privacy auditing is complete when CI is green with this evidence. PRODUCT.md release gate 10 is **not** complete until the published privacy policy and Google Play Data Safety declaration are compared with the exact release behavior and approved in the Play account.
