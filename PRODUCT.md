# EclipseCam

> Your phone becomes the ultimate eclipse camera.

## Vision

EclipseCam transforms an Android phone into a complete eclipse companion.

The user should be able to:

- find the best observing location
- prepare the phone before eclipse day
- automatically capture the eclipse
- enjoy the event instead of operating the phone
- leave with the best possible photos and timelapse

The application is phone-centric.

Everything revolves around the user and their phone.

The eclipse engine exists only to improve the user's experience.

## Product principles

### 1. Phone first

The phone is the camera, GPS instrument, compass, inclinometer, gyroscope, AR viewer, timelapse recorder, local storage device, and sharing tool.

The primary experience starts with the camera and the user's immediate next action, not with menus or abstract astronomy.

### 2. Local first

Everything possible is computed and stored locally, including:

- eclipse prediction
- Besselian calculations
- eclipse contacts
- shadow position
- observer optimisation
- AR projection
- trajectory prediction
- automated capture planning
- timelapse and media generation

Network access is optional. Google Maps may provide the online basemap, but eclipse geometry, GPS processing, capture logic, plans, media, and user data must not depend on a backend.

### 3. User first

Do not expose astronomical complexity unless the user asks for it.

The app should answer practical questions:

- Where should I stand?
- What will I see from here?
- Where should I point the phone?
- Will the full eclipse remain in frame?
- Is the phone ready and stable?
- What happens next?
- Can I stop touching the phone and enjoy the eclipse?

### 4. Automation first

The ideal sequence is:

1. Open EclipseCam.
2. Mount and point the phone.
3. Follow simple visual alignment guidance.
4. Confirm the complete eclipse trajectory fits.
5. Arm automatic capture.
6. Enjoy the eclipse.
7. Receive locally generated photos, timelapse, montage, and shareable results.

The user should touch the phone as little as possible after arming.

### 5. Safety

EclipseCam must encourage safe solar observation and photography.

- Solar-filter warnings must be unambiguous.
- The app must never imply that an unfiltered camera or direct viewing is safe during partial phases.
- Totality-specific filter guidance must be conservative and require explicit acknowledgement.
- Safety instructions must remain accessible during capture.

### 6. Scientific accuracy

Predictions must be based on validated astronomical models and trusted eclipse data.

- Use Besselian elements for local circumstances and path geometry.
- Validate results against authoritative reference cases.
- Display meaningful uncertainty instead of false precision.
- Scientific correctness takes priority over visual effects and release deadlines.

### 7. Outdoor usability

The app is designed for bright, stressful, time-critical outdoor conditions.

- large touch targets
- sunlight-readable contrast
- minimal text
- one obvious primary action per screen
- clear countdowns and status
- audio and vibration cues where useful
- no deep navigation during the eclipse

### 8. Privacy

User data belongs to the user.

- no account required
- no advertising
- no behavioural tracking
- no analytics SDK by default
- no automatic upload
- no location transmission to an EclipseCam backend
- no photo transmission
- explicit user action before sharing
- option to remove location metadata from exports

### 9. Honest capability

EclipseCam must detect and communicate what the current phone can actually do.

Camera controls, RAW support, manual exposure, lens choices, capture rates, thermal limits, battery capacity, storage, sensor accuracy, and background behaviour vary by device. The app must adapt its plan and never promise unsupported behaviour.

## Core experience

### Camera

The default and primary surface.

It provides:

- live CameraX preview
- current Sun direction
- future eclipse trajectory
- C1, C2, maximum, C3, and C4 positions
- predicted Moon/Sun overlap
- framing and field-of-view guidance
- heading, elevation, and roll corrections
- lens and orientation recommendations
- readiness checks
- automatic capture arming

### Live

A glanceable instrument panel showing:

- current eclipse phase
- next contact and countdown
- next photo countdown
- GPS and sensor confidence
- capture status
- battery, thermal, and storage state
- movement or framing warnings

### Position

An observer-centric map showing:

- the user's GPS position and uncertainty
- bold eclipse centreline
- northern and southern limits
- moving shadow
- distance and direction to the path and centreline
- duration gained by moving
- Google Maps online basemap
- independent offline eclipse map packs and geometry fallback

### Gallery

A local session library containing:

- original images
- selected frames
- timelapse video
- eclipse montage
- capture report and session metadata
- explicit Android sharing
- privacy controls for exported location data

## Product promise

> Mount the phone. Point it once. EclipseCam does the rest.

## Non-goals

EclipseCam is not:

- a general social network
- an advertising platform
- a cloud photo service
- a generic astronomy encyclopedia
- a replacement for professional solar-observation safety equipment
- an app that forces the user to watch a screen during totality

## Release evaluation

Every release must be evaluated against this document.

A change belongs in EclipseCam only when it materially improves at least one of:

- positioning against the eclipse
- scientific prediction
- phone alignment or framing
- capture automation
- photo or timelapse quality
- safety
- outdoor usability
- privacy
- reliability
- the user's ability to enjoy the eclipse without operating the phone

Changes that add complexity without advancing one of these outcomes should not be merged.

## Release gates

A release cannot be described as production-ready until all applicable gates pass:

1. Scientific calculations are validated against authoritative reference cases.
2. Centreline and eclipse limits are accurate and clearly distinguished.
3. GPS, orientation, and camera behaviour are tested on physical phones.
4. Automatic capture survives the intended session duration.
5. Battery, thermal, storage, and interruption failures are handled safely.
6. Camera capability detection prevents unsupported plans.
7. Offline core functionality works without network access.
8. Google Maps is the only intended network-backed runtime feature.
9. No secrets or signing material exist in source control.
10. Privacy policy and Play Data Safety declarations match actual behaviour.
11. The uploaded Android App Bundle passes tests, lint, signing, and Play pre-launch checks.
12. A real user can complete the core flow without developer assistance.

## Success measure

The ultimate compliment is:

> I completely forgot about the app during totality.

That means EclipseCam prepared the phone, captured the event, and allowed the user to experience it directly.
