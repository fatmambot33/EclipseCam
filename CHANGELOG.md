# Changelog

All notable changes to EclipseCam will be documented in this file.

The format follows Keep a Changelog and Semantic Versioning.

## [Unreleased]

### Added
- Permission-aware, local-only Android location repository
- GPS provider, accuracy, altitude, capture time and staleness state
- Mockable location flow connected to observer-to-centreline guidance
- Graceful permission-denied, unavailable-provider and acquisition states

### Validation still required
- Android permission-transition instrumentation tests
- Physical GPS and accuracy validation on the Pixel 7 Pro

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
