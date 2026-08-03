# Parallel agent boundaries

Agents should work in separate packages and branches wherever possible:

- `astronomy/` — Besselian evaluation, local circumstances, path geometry, observer guidance.
- `device/` — GPS, orientation, stability, battery, thermal and storage state.
- `camera/` — CameraX preview, capability inventory, capture controls.
- `ar/` — projection, field of view, trajectory overlay and alignment guidance.
- `capture/` — foreground service, scheduler, recovery and safeguards.
- `map/` — Google Maps presentation and local overlays.
- `media/` — session repository, gallery, timelapse and montage.
- `ui/` — shared design system, accessibility and navigation.

Cross-package interfaces should be introduced in small contracts rather than broad refactors. Each pull request must reference its issue and explain any boundary crossing.
