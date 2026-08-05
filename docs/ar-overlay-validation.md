# AR overlay validation

## Automated checks

The deterministic projection tests cover centre projection, field-of-view boundaries, portrait and landscape viewports, phone roll, behind-camera targets, framing margins, directional corrections, and low-confidence fallback.

`EclipseTrajectoryOverlayTest` acts as a stable overlay snapshot at the presentation-model boundary. It locks contact-marker order, pixel coordinates, viewport clipping, fit state, and user guidance without depending on GPU-specific screenshot output.

## Pixel 7 Pro outdoor alignment protocol

Run this check with the back camera, location enabled, automatic rotation enabled, and a certified solar filter fitted whenever the Sun is used as the target.

1. Place the phone on a stable tripod and wait for HIGH or MEDIUM orientation confidence.
2. Confirm the projected Sun marker is within 2 degrees of the filtered solar image in portrait.
3. Rotate to landscape without moving the tripod head and confirm the marker remains within 2 degrees.
4. Repeat with the available rear lenses and verify the full contact arc fit state changes only when the selected field of view changes.
5. Move the target outside each viewport edge and confirm the guidance direction is correct.
6. Introduce magnetic interference and confirm precise markers disappear when confidence becomes LOW or UNAVAILABLE.
7. Record device model, Android version, lens, measured angular error, confidence, and pass/fail.

Physical alignment is a release qualification step because it depends on the individual device, magnetic environment, camera calibration, mount, and weather. The app must never claim precise alignment while confidence is low.
