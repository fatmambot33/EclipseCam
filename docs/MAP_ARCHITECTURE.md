# Map architecture

EclipseCam uses MapLibre Native as the embedded Android renderer and OpenStreetMap-derived vector data as the basemap.

## Rules

- Eclipse geometry is app-owned local data and remains visible without a basemap.
- The centreline must visually dominate the northern and southern path limits.
- Every OpenStreetMap-derived source must display `© OpenStreetMap contributors` and expose licence information.
- Tile sources are runtime configuration; credentials are never committed.
- `tile.openstreetmap.org` is forbidden as an application backend and must never be used for bulk or offline downloads.
- Offline downloads require a provider whose terms explicitly permit them, self-hosted tiles, or packaged regional data.
- Google Maps may only be invoked through an external navigation intent; it is not the embedded renderer.

## Current foundation

`MapTileSource` validates transport, attribution, and the public OSM tile-server prohibition. `EclipseMapOverlay` keeps centreline and path limits independent from tile availability. A later UI layer will translate these contracts into MapLibre sources and layers.

## Provider decision still required

Before enabling production online or offline tiles, select a provider and record:

1. vector style URL and authentication model;
2. attribution wording;
3. online request limits;
4. offline download rights and storage limits;
5. retention and privacy terms;
6. failure and deprecation policy.
