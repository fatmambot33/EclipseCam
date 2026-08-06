# Camera capture failure policy

The CameraX backend must translate framework errors into the stable transactional capture contract before returning control to the capture engine.

## Recoverable failures

- camera closed during a frame
- transient capture failure

These outcomes pause the durable session so the user can explicitly resume after the camera becomes available again.

## Fatal failures

- JPEG file I/O failure
- selected camera no longer valid
- unknown or unmapped backend failure

These outcomes fail the durable session because output integrity or the configured hardware contract can no longer be guaranteed.

Backend messages are preserved when present. Blank messages use deterministic user-safe fallback reasons. The policy does not replace CameraX hardware integration or physical Pixel 7 Pro validation; it provides the tested error boundary required by that adapter.
