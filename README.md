# CitizenEye

Native Android app for helping French citizens follow their député’s votes and actions.

## Current product behavior

The app no longer uses seeded demo representatives or demo votes.

On first use, the user enters either:

- a postal code, for example `92000`; or
- a city name, for example `Nanterre`.

CitizenEye then loads public data from live sources:

- `geo.api.gouv.fr` for French commune/postal-code lookup;
- data.gouv.fr constituency boundary GeoJSON for GPS point-in-polygon circonscription resolution;
- Assemblée nationale Open Data for deputies in office;
- Assemblée nationale Open Data for public scrutin/vote positions.

Deputies and votes are stored locally on-device after the first download. The local copy is reused for 24 hours, then refreshed once per day from public sources. If the public source is temporarily unavailable during a daily refresh, CitizenEye keeps using the stale local copy instead of failing the user flow.

If the location can map to several constituencies, CitizenEye shows the possible deputies and asks the user to choose. This is intentional: postal code / city name alone is not always enough to identify one député with legal certainty.

## Current implementation scope

Implemented:

- real network-backed postal-code/city onboarding with autocomplete preview;
- optional device geolocation to prefill city/postal code and, when available, resolve the precise legislative circonscription from official boundary geometry without auto-validating;
- live commune lookup;
- live deputy loading from Assemblée nationale XVIIe legislature data;
- ambiguity screen listing deputies for the relevant department;
- deputy selection;
- official rounded deputy portraits on the home representative card and statistics profile;
- live current-legislature public votes for the selected député, displayed progressively 20 at a time;
- tap-through deputy profile statistics page with participation and vote-position distribution calculated on-device across the loaded XVIIe legislature vote set;
- source-aware vote summaries with scrutin number and counts;
- on-device daily cache for Assemblée deputies and votes;
- stale-cache fallback when public data refresh temporarily fails;
- unit tests for input validation and daily cache freshness;
- debug APK build.

Not yet implemented:

- exact address/street-level constituency resolution when GPS is unavailable or not the user’s home location;
- persistent saved député after first launch;
- server-side cache/normalization layer for faster production imports;
- vote-detail drill-down screen;
- notifications.

## Product caveat

A city or postal code can contain multiple legislative constituencies. If the user opts into geolocation and Android has a usable recent lat/lon, CitizenEye resolves the point against public constituency boundary geometry and proposes the matching député for confirmation. Without GPS, or when the position may not be the home address, the app stays honest and asks the user to choose or edit instead of pretending certainty.

## Run / install

On macOS with Android Studio installed:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug
```

Install on attached Android device:

```bash
export ANDROID_HOME=/Users/romains/Library/Android/sdk
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell monkey -p com.citizeneye -c android.intent.category.LAUNCHER 1
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Next engineering steps

1. Add a server-side importer/normalizer so mobile can consume small JSON APIs instead of parsing large Assemblée zip archives locally.
2. Add exact constituency resolution from address/commune geometry.
3. Persist the selected député locally and refresh votes on app open.
4. Add vote detail screens with source URL, date, scrutin number, group breakdown and deputy position.
5. Add lightweight notifications only after the user trusts the data.
