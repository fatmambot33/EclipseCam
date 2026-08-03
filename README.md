# EclipseCam

EclipseCam is a phone-first Android application for planning, aligning and automatically capturing a solar eclipse while the user enjoys the event.

## Product promise

Mount the phone, follow the visual alignment guide, arm the session, and let EclipseCam photograph the eclipse locally.

The eclipse model, GPS processing, AR-style overlays, camera automation, media generation and saved plans run on-device. Google Maps is the only permitted network-backed component and is used only as an optional basemap.

## Current status

Version `1.0.0-alpha01` is a development build, not a public scientific release.

Implemented foundations include:

- Kotlin, Jetpack Compose and Material 3
- observer-centric `PersonalEclipseState`
- native Android GPS/location tracking
- local solar-position and path calculations
- Google Maps basemap with locally rendered eclipse overlays
- CameraX preview
- phone alignment guidance
- automatic phase-sensitive JPEG capture
- local MediaStore session folders
- explicit Android sharing
- no account, analytics, advertising or backend

The current eclipse path model is still approximate. Public release is blocked until the local Besselian solver and its reference validation suite are complete.

## Product surfaces

- **Camera:** default screen for preview, future eclipse overlay, alignment and automatic capture.
- **Live:** current phase, next contact, GPS confidence and session status.
- **Position:** observer-centric map, eclipse shadow and position optimisation.
- **Gallery:** local sessions, timelapses, montages and explicit sharing.

## Plans and release gates

- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md)
- [`docs/RELEASE_GATES.md`](docs/RELEASE_GATES.md)
- [`docs/PRODUCT_PLAN.md`](docs/PRODUCT_PLAN.md)
- [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md)

## Phone-only cloud builds

EclipseCam includes a manually triggered GitHub Actions workflow that creates an installable debug APK or a signed Google Play `.aab` from a phone. See [`docs/PHONE_BUILD_WORKFLOW.md`](docs/PHONE_BUILD_WORKFLOW.md).

## Build

Create `local.properties` outside version control:

```properties
MAPS_API_KEY=YOUR_RESTRICTED_ANDROID_MAPS_KEY
```

For a release build, provide the signing environment variables documented in `RELEASE_CHECKLIST.md`, then run:

```bash
./gradlew clean test lint bundleRelease
```

Physical-device testing is required for GPS, orientation, camera capabilities, thermal behaviour and multi-hour capture reliability.
