# Local session indexing

`LocalSessionIndex` scans only the app-private capture output root. It never uploads, syncs, or shares media.

Each capture-session directory appears in the index even when it is empty or interrupted, so the Gallery can explain incomplete sessions instead of silently hiding them. Only non-empty `.jpg` files are exposed as assets; temporary zero-byte reservations and unrelated files are ignored.

A session is complete only when it has at least one readable JPEG and contains a `session.complete` marker. The capture runtime must create that marker only after the durable session checkpoint reaches `COMPLETED`. Missing markers preserve recoverability and prevent interrupted work from being represented as finished.

Sessions are ordered by newest asset modification time, then by stable session ID. The Gallery UI should consume this contract without reading arbitrary external storage.

## Remaining validation

- Connect the index to the Gallery screen.
- Add MediaStore/export instrumentation tests.
- Verify incomplete-session presentation and JPEG playback on Pixel 7 Pro.
- Keep explicit sharing and location-metadata removal separate from indexing.
