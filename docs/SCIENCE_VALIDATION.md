# Scientific validation

## Supported event

EclipseCam currently embeds one scientific model: the total solar eclipse of 12 August 2026.

The runtime model is local-first and requires no network access. The embedded coefficients are copied from the current NASA/GSFC Solar Eclipse Search Engine dataset for eclipse `20260812`, updated 30 October 2023.

Required acknowledgement for reproduced data:

> Eclipse Predictions by Fred Espenak, NASA's GSFC

## Authoritative source set

The model is pinned to the NASA/GSFC values below:

- reference epoch: 18.000 TDT
- delta T: 75.4 seconds
- greatest eclipse: 17:45:51 UTC
- ephemerides: VSOP87/ELP2000-82
- Besselian polynomials: `x`, `y`, declination `d`, penumbral radius `l1`, umbral radius `l2`, and hour angle `mu`
- lunar-radius constants: `k1 = 0.272488`, `k2 = 0.272281`
- validity window: 15:00 through 21:00 TDT

Primary references:

- `https://eclipse.gsfc.nasa.gov/SEsearch/SEdata.php?Ecl=20260812`
- `https://eclipse.gsfc.nasa.gov/SEpath/SEpath2001/SE2026Aug12Tpath.html`
- `https://eclipse.gsfc.nasa.gov/SEmono/reference/locircT.html`

## Repository controls

`BesselianElementsAuthoritativeTest` locks the exact published coefficients, source dataset identity, delta T, greatest-eclipse instant, and reference-epoch evaluation.

`BesselianLocalCircumstancesCalculatorTest` checks deterministic output, validity-window rejection, ordered contacts, total, partial and no-eclipse geometries, normalized horizontal coordinates, and five city-level reference cases.

## Tolerances and uncertainty

NASA's city-level tables commonly round contact times to whole minutes. City regression fixtures therefore use a 90-second tolerance. This tolerance tests for material drift; it is not the uncertainty shown to users.

NASA states that northern and southern path edges are limited to approximately 1–2 km by the lunar limb profile. EclipseCam currently reports a conservative 3 km path uncertainty until limb-profile modelling and physical path-edge verification are implemented.

The embedded polynomial model must not be used outside its published six-hour validity window. Input outside that window fails explicitly.

## Release-gate status

The coefficients and local numerical model are repository-tested, but the scientific release gate is not complete until:

- local contact times, magnitude, obscuration and Sun coordinates are independently checked against high-precision authoritative fixtures rather than only minute-rounded city tables;
- centreline and both path limits are compared against the NASA path table across the full supported region;
- path-edge uncertainty and lunar-limb assumptions are reviewed and documented;
- results are verified on the final application build.

No production-readiness claim should be made before those checks pass.
