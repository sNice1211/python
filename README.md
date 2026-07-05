# NexradWx

An Android app for weather enthusiasts who want raw radar data, not a rendered summary:
NEXRAD Level II moments (reflectivity, velocity, correlation coefficient) decoded from NOAA's
public archive, plus raw surface station observations from the National Weather Service.

## Modules

- **`nexrad-core`** — pure Kotlin/JVM library, no Android dependency. This is where all the
  hard, easy-to-get-wrong logic lives, and it has a real unit test suite (28 tests):
  - `decode/Level2Decoder.kt` — a from-scratch NEXRAD Level II Archive decoder (ICD 2620002):
    volume header, bzip2-framed LDM records, Message Type 31 (Digital Radar Data Generic
    Format), generic data moment blocks for REF/VEL/SW/ZDR/PHI/RHO. Verified against a
    byte-accurate synthetic fixture (`src/test/resources/build_fixture.py` +
    `Level2DecoderTest.kt`) that exercises bzip2 framing, moment scaling, below-threshold
    (NaN) and range-folded (+Inf) sentinels, and radial status flags.
  - `color/ColorTables.kt` — dBZ / velocity / correlation-coefficient color ramps.
  - `render/PpiRasterizer.kt` — polar-to-pixel PPI rasterizer (radar-centered, north-up),
    independent of any UI toolkit so it's unit-testable and reusable.
  - `site/RadarSite.kt` — catalog of 162 WSR-88D sites (CONUS, Alaska, Hawaii, Guam, Puerto
    Rico, and the overseas military units), sourced from NOAA's Historical Observing Metadata
    Repository via the py-ART project's `nexrad_common.py` (BSD-3).
- **`app`** — the Android application (Kotlin + Jetpack Compose): site picker, a 3-tab PPI
  view (Reflectivity / Velocity / Correlation Coefficient), and a station browser with raw
  METAR-derived observations.

## Data sources (both free, no API key)

- **Radar**: `s3://unidata-nexrad-level2` (Unidata's public mirror of the NOAA archive,
  anonymous/no-sign-request). NOAA's own `noaa-nexrad-level2` bucket -- long documented as *the*
  canonical public archive, and what this app originally used -- now returns `AccessDenied` for
  all anonymous requests, confirmed directly against S3 (not a proxy/sandbox artifact: verified
  from two independent networks, including a real device). Unidata's mirror has the same key
  layout and is still genuinely public, but syncs with a multi-hour lag rather than the
  ~4-10 minutes NOAA's bucket used to offer, so this is raw data but noticeably delayed.
  Cutting that lag means reading Unidata's separate real-time chunk feed instead
  (`s3://unidata-nexrad-level2-chunks`, keys like `<SITE>/<volume>/<yyyyMMdd>-<HHmmss>-<seq>-S`),
  reassembling the S/I/E sequence per volume -- confirmed reachable and public, but not
  implemented here.
- **Stations**: `api.weather.gov` (National Weather Service). **Before shipping**, replace the
  placeholder `USER_AGENT` in `NwsStationRepository.kt` with your own app name/contact — NWS
  asks every client to self-identify.

## Scope decisions worth knowing about

- Only REF/VEL/RHO are surfaced in the UI (per the ask); SW/ZDR/PHI are already decoded by
  `Level2Decoder` and available on `Radial.moments` if you want to add more tabs.
- No on-device GPS integration yet — the station browser takes a typed lat/lon instead of
  requesting `ACCESS_FINE_LOCATION` at runtime. The permissions are declared in the manifest
  for when that's wired up.
- Velocity/spectrum-width units are left in the ICD-native m/s rather than converted to knots.

## Building

Requires a JDK 17+ and the Android SDK (compileSdk 34). This repo's Gradle wrapper
(`./gradlew`) is fully set up:

```
./gradlew :nexrad-core:test   # runs the decoder/color-table/rasterizer/catalog unit tests
./gradlew :app:assembleDebug  # requires the Android SDK
```

### A note on how this was built

This project was developed in a sandboxed environment with no Android SDK and no network
access to `dl.google.com` (where the Android Gradle Plugin and SDK components are hosted), so
`:app` could not be compiled or run there. `:nexrad-core` has **no** Android dependency, so it
*was* fully buildable and testable in that sandbox — all 28 tests pass. The `:app` module's
Compose/ViewModel/networking code was written and carefully reviewed by hand but is
**unverified by a real compiler**; run `./gradlew :app:assembleDebug` (or open in Android
Studio) as the first step on a real machine, and treat that as part of the initial review.
