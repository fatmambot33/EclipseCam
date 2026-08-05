# AR overlay integration contract

`CameraPreviewSurface` always owns a full-size, non-interactive overlay layer above the CameraX preview.

Callers provide a `FramingAssessment` produced by `ProjectionEngine.assessTrajectory`. When the assessment is absent, the overlay displays an explicit unavailable message instead of invented marker positions.

Visible trajectory samples are rendered in sample order. Contact markers use their sample identifiers, with `MAX` emphasized. Samples outside the viewport are retained in the semantic snapshot but are not drawn. Behind-camera and unavailable samples are not converted to marker coordinates.

The overlay exposes a deterministic semantic snapshot containing fit state, guidance, marker order, integer pixel positions, and clipping state. This is the stable screenshot-test boundary; visual GPU rendering remains intentionally thin.
