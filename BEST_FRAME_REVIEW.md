# Best-frame review assistance

EclipseCam now provides a deterministic local shortlist of original captures to help a user review likely-interesting frames without scrolling an entire eclipse session.

## Contract

`LocalBestFrameReviewAssistant` uses only persisted capture-plan metadata already stored with local sessions. It does not inspect image pixels, upload media, or claim that a shortlisted frame is objectively the sharpest or best exposed image.

The shortlist:

- prioritizes totality, then contact bursts, then partial-phase captures;
- selects at most three representative captures per phase by default;
- distributes selections across the available instruction range within each phase;
- ignores captures whose phase or instruction metadata is unavailable instead of guessing;
- works for complete, failed, paused, and interrupted local sessions;
- never modifies original captures.

This is intentionally review assistance rather than automatic image-quality scoring. Pixel-level sharpness, blur, clipping, exposure, and occlusion analysis would require a separately validated image-analysis milestone before EclipseCam could make stronger quality claims.

## Release status

This advances the `Best-frame selection assistance` item in issue #1 and the local-media product experience. It does not satisfy physical Pixel 7 Pro media playback, long-session reliability, Play Console, privacy publication, offline-provider, or real-user release gates in `PRODUCT.md`.
