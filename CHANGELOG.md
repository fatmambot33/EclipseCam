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
- Lifecycle-bound CameraX preview on the Camera surface with back-camera preference and front-camera fallback
- Explicit preview startup, streaming, permission and unavailable states with JVM regression coverage
- Local battery, charging, thermal-pressure and available-storage snapshot reader
- Deterministic ready, degraded and blocked capture safeguard policy with boundary tests
- Deterministic east-north-up world-to-screen projection for Sun and eclipse contact markers
- Lens field-of-view framing assessment, roll mapping, fit confidence and simple directional guidance
- AR projection validation documentation and JVM coverage for portrait, landscape, clipping, behind-camera and low-confidence cases
- Bounded phase-sensitive capture plan derived from validated C1/C2/MAX/C3/C4 contacts
- Conservative arming gate for model validity, orientation stability, framing, solar-filter acknowledgement and device health
- Framework-neutral local capture-session checkpoints with deterministic pause, resume, completion and failure recovery
- Recovery validation that rejects mismatched plans, invalid indexes and inconsistent counters
- Non-exported camera foreground service with a persistent notification and explicit pause, resume and stop actions
- Deterministic foreground-service lifecycle reducer with JVM regression coverage
- Versioned local capture-checkpoint codec with strict corrupt-data rejection
- Atomic file-backed checkpoint persistence with overwrite, clear, missing, and corruption coverage
- Capture-session coordinator that atomically persists arming, progress, pause, resume, completion, and failure transitions
- Deterministic restoration that rejects corrupt or plan-incompatible local session state before execution
- Framework-neutral capture execution engine that processes at most one due instruction per tick
- Persisted safe pause on blocked device health or recoverable camera errors, fatal failure recording, and deterministic late-step skipping
- Phase-aware degraded-health shedding that skips routine partial captures while preserving contact bursts and totality
- Deterministic foreground-service tick scheduling with immediate backlog draining and bounded future wake-ups
- Conservative capture capability gate that selects a compatible rear camera before arming
- Explicit blocking when JPEG output or contact/totality bracketing controls are unavailable
- Capture service orchestrator that keeps foreground-service commands, durable session state, and execution outcomes synchronized
- Recoverable stop behavior that persists a paused checkpoint instead of discarding an active session
- Versioned capture-plan codec with strict instruction and chronology validation
- Atomic file-backed capture-plan persistence for process-recreation recovery
- Foreground-service recovery bootstrap backed by the validated durable plan/checkpoint bundle
- Fail-closed sticky restart behavior that restores active sessions paused until explicit resume
- Durable foreground-service pause, resume, and stop command synchronization
- Process-recovery normalization that persists previously running sessions as paused
- App-private, collision-safe JPEG output reservation with stable UTC filenames and failed-capture cleanup
- Deterministic camera capture requests for JPEG size, focus, white balance, and phase-sensitive exposure programs
- Conservative -2/0/+2 EV bracketing with manual-sensor fallback and explicit unsupported results
- Atomic per-instruction capture sequence planning with ordered exposure frames and rollback of partial output reservations
- Transactional camera sequence execution with ordered frame capture and explicit recoverable or fatal outcomes
- Whole-bracket output cleanup after preparation, frame, backend, or close failures
- CameraX callback error adaptation into stable recoverable and fatal capture outcomes
- Fail-closed handling for unknown future CameraX image-capture error codes

### Changed
- Updated the embedded 12 August 2026 model from the older 2014 NASA element set to the current NASA/GSFC dataset using delta T 75.4 seconds and greatest eclipse at 17:45:51 UTC
- Replaced Android `Size` values in the camera capability contract with stable framework-neutral output dimensions
- Updated the Camera surface to show honest live-preview state instead of a disabled placeholder

### Validation still required
- Android permission-transition instrumentation tests
- Physical GPS and accuracy validation on the Pixel 7 Pro
- Android orientation instrumentation tests
- Physical orientation validation on the Pixel 7 Pro in portrait, landscape, flat, mounted and moving states
- Independent high-precision local-circumstances and full path-edge validation
- CameraX preview lifecycle instrumentation, orientation-change testing and physical validation of every Pixel 7 Pro rear lens
- CameraX execution validation for focus, exposure bracketing, white-balance locking, and output size on every Pixel 7 Pro rear lens
- Long-session battery drain, charging, storage exhaustion and real thermal-throttling validation on the Pixel 7 Pro
- AR overlay screenshot tests and outdoor Pixel 7 Pro alignment validation against known Sun positions
- Foreground capture service lifecycle, screen-off recovery and long-duration Pixel 7 Pro validation

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
