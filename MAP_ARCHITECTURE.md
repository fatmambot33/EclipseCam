# Position map architecture

EclipseCam renders eclipse-critical geometry locally with MapLibre Native. The eclipse centreline, northern and southern limits, observer marker, and GPS accuracy ring do not depend on a network basemap.

## Local-first rendering

`EclipseMapScene2026` converts the validated 12 August 2026 reference path into a provider-independent `EclipseMapOverlay`. `ObserverEclipseMap` renders that geometry from local GeoJSON sources. The centreline is deliberately wider and more prominent than the path limits, and the observer accuracy area is visually separate from the documented 3 km path-limit uncertainty.

When no tile source is configured, MapLibre loads a minimal local style with no network sources. The Position surface still shows the eclipse path and labels the state as `Offline eclipse geometry • no basemap`. This is the required fail-open visual behaviour for the local eclipse geometry, not a claim that an offline OSM basemap pack is installed.

## Optional basemap contract

A basemap is optional and must enter through `MapTileSource`. The contract requires an HTTPS or packaged asset style, OpenStreetMap attribution, and rejects the public `tile.openstreetmap.org` service. No provider URL, API key, token, or account credential is embedded by this milestone.

Before enabling a hosted OSM-derived basemap in production, the owner must select a provider whose terms permit the intended application and offline use, record attribution/licence requirements, and supply any credential outside source control. Offline downloadable packs remain tracked separately by issue #10.

## Observer guidance

The Position screen consumes `AndroidLocationRepository` through `ObserverGuidanceFlow`. A fresh local location sample drives the observer marker, reported Android horizontal-accuracy ring, distance and bearing to the nearest reference centreline point, reference central duration, and inside/outside-path status. Stale and low-accuracy fixes are called out explicitly.

## Validation boundary

JVM tests verify path conversion, uncertainty preservation, accuracy-ring geometry, and rejection of the public OSM tile server. Android instrumentation verifies that the Position screen exposes the MapLibre surface and local fallback label with no configured basemap.

A licensed tile provider, airplane-mode/offline-pack validation, representative map screenshots at multiple zoom levels, and physical Pixel 7 Pro GPS/outdoor checks remain release evidence and must not be inferred from emulator CI.
