# Changelog

All notable changes to EclipseCam will be documented in this file.

The format follows Keep a Changelog and Semantic Versioning.

## [Unreleased]

### Added
- Permission-aware, local-only Android location repository
- GPS provider, accuracy, altitude, capture time and staleness state
- Mockable location flow connected to observer-to-centreline guidance
- Graceful permission-denied, unavailable-provider and acquisition states
- Deterministic rotation-vector processing for azimuth, elevation and roll
- Display-rotation remapping, sensor confidence and explicit unavailable fallback
- Lifecycle-scoped Android orientation repository using rotation-vector and gyroscope sensors
- Rotation-vector fallback and safe unavailable state when orientation sensors cannot be registered
- Unit coverage for orientation normalization, landscape mapping, gyroscope motion and invalid samples
- Scientific validation document with authoritative source, tolerance and release-gate status
- Exact regression tests for the current NASA/GSFC 12 August 2026 Besselian dataset
- Explicit solver diagnostics for converged, no-eclipse and failed numerical outcomes
- Deterministic camera capability mapping for lens facing, orientation, zoom, JPEG sizes, RAW, manual exposure, focus and exposure compensation
- JVM regression coverage for complete, missing and invalid camera hardware metadata

### Changed
- Updated the embedded 12 August 2026 model from the older 2014 NASA element set to the current NASA/GSFC dataset using delta T 75.4 seconds and greatest eclipse at 17:45:51 UTC
- Replaced Android `Size` values in the camera capability contract with stable framework-neutral output dimensions

### Validation still required
- Android permission-transition instrumentation tests
- Physical GPS and accuracy validation on the Pixel 7 Pro
- Android orientation instrumentation tests
- Physical orientation validation on the Pixel 7 Pro in portrait, landscape, flat, mounted and moving states
- Independent high-precision local-circumstances and full path-edge validation
- CameraX preview lifecycle instrumentation and physical validation of every Pixel 7 Pro rear lens

## [0.0.1] - 2026-08-03

### Added
- Initial Android project foundation
- Material 3 application structure
- Package name `com.fatmambo33.eclipsecam`
- PRODUCT.md product constitution
- GitHub Actions CI pipeline
- Google Play publishing workflow
- Local-first application architecture
- CameraX integration foundation
- Observer-centric eclipse model foundation
- Google Maps integration framework

### Changed
- Established release pipeline and versioning strategy.

### Known limitations
- Eclipse calculations are not yet based on the final validated Besselian implementation.
- AR guidance, automated timelapse, gallery and production camera workflow are still under development.
- Intended for technical preview and pipeline validation only.

### Next
- Validated astronomy engine
- Accurate centreline and eclipse limits
- AR framing guidance
- Automated eclipse capture
- Local timelapse generation
