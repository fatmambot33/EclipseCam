# AR projection validation

EclipseCam's AR projection core is deterministic, local-only, and independent of Android UI and camera APIs.

## Coordinate model

- Sky directions use azimuth clockwise from true north and elevation above the local horizon.
- Directions are converted to east-north-up unit vectors.
- The camera basis is derived from device azimuth, elevation, and roll.
- Perspective projection uses the selected lens horizontal and vertical field of view.
- Viewport dimensions determine final pixel coordinates, so the same engine supports portrait and landscape surfaces.

Targets behind the camera are reported explicitly. Targets outside the selected lens field of view still return projected coordinates with `insideViewport = false`, allowing the UI to draw directional indicators without pretending the target is visible.

## Framing contract

`ProjectionEngine.assessTrajectory` accepts ordered current/future Sun or contact samples such as C1, C2, MAX, C3, and C4. It returns:

- projected marker results
- `FITS`, `CLIPPED`, `BEHIND_CAMERA`, or `UNAVAILABLE`
- shortest horizontal correction
- vertical tilt correction
- roll correction
- a simple user-facing guidance message

A configurable edge margin prevents a trajectory touching the image boundary from being described as safely framed.

## Confidence handling

High and medium orientation confidence permit projection. Low or unavailable orientation confidence returns `UNAVAILABLE` and suppresses directional corrections. EclipseCam must not show false-precision alignment guidance when sensor confidence is inadequate.

## Current validation

JVM regression tests cover:

- centre projection
- field-of-view boundaries
- targets behind the camera
- roll transformation
- portrait dimensions and narrow/wide lens behaviour
- full-trajectory fit and clipping
- directional guidance
- north-crossing angle normalization
- low-confidence fallback
- invalid input rejection

## Release validation still required

Issue #6 remains open until the projection engine is connected to contact/trajectory overlay composables, screenshot tests pass, and outdoor Pixel 7 Pro alignment is compared against known Sun positions in portrait and landscape with each supported rear lens.
