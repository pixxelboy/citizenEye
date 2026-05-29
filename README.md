# CitizenEye

CitizenEye is a citizen-first Android app that helps people in France see what their député does at the Assemblée nationale.

It is politically opinionated in one way: public power should be easy to inspect. The app is not attached to any party, campaign, or parliamentary group. It exists to make votes, representatives, and public records understandable without turning democracy into an outrage feed.

[Version française](README.fr.md)

## Screenshots

<p align="center">
  <img src="docs/screenshots/onboarding.svg" width="30%" alt="CitizenEye onboarding with GPS constituency confirmation" />
  <img src="docs/screenshots/home.svg" width="30%" alt="CitizenEye home screen with representative card and public votes" />
  <img src="docs/screenshots/profile.svg" width="30%" alt="CitizenEye statistical profile screen" />
</p>

## Why this exists

Most citizens do not know who represents them, how that person votes, or where to check the official record. The data exists, but it is spread across public repositories, large archives, institutional pages, and legal identifiers.

CitizenEye turns that into a simple flow:

1. Enter a postal code or use geolocation.
2. Confirm the inferred city / department / constituency.
3. See the député who represents that place.
4. Follow recent public votes and the deputy's recorded position.
5. Open a concise statistical profile built from official public vote data.

The goal is not to tell citizens what to think. The goal is to make it harder for anyone to hide behind complexity.

## What works today

CitizenEye is already a real-data Android MVP, not a seeded demo.

- Native Android app written in Kotlin + Jetpack Compose.
- Postal-code and city lookup through official French commune data.
- Optional geolocation autocomplete.
- GPS point-in-polygon constituency detection using public constituency geometry.
- Explicit confirmation before selecting a location.
- Ambiguity handling when a city/postal code can span multiple constituencies.
- Active député loading from Assemblée nationale open data.
- Official rounded deputy portraits.
- Current-legislature public votes loaded from Assemblée nationale data.
- Progressive vote feed, 20 votes at a time.
- Deputy statistical profile:
  - public-scrutin participation;
  - Pour / Contre / Abstention / Non-votant distribution;
  - clear caveat that these are not performance scores.
- On-device daily cache for large public datasets.
- Stale-cache fallback when a public source is temporarily unavailable.
- Unit tests for lookup, cache, deputy photos, vote pagination, stats, and constituency geometry.

## Open data sources

CitizenEye is built on public-interest data. The important sources are:

| Data | Source | Used for |
| --- | --- | --- |
| Commune, postal code, department, INSEE metadata | https://geo.api.gouv.fr | City/postal-code onboarding and display labels |
| French administrative API documentation | https://geo.api.gouv.fr/decoupage-administratif | Source reference for commune/departement lookups |
| Assemblée nationale open data portal | https://data.assemblee-nationale.fr | Deputies, mandates, organs, public votes |
| Active deputies / mandates / organs archive | https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip | Active deputy identity, group, department, constituency |
| Scrutins and individual vote positions | https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip | Public votes and each deputy's recorded position |
| Legislative constituency boundaries | https://static.data.gouv.fr/resources/contours-geographiques-des-circonscriptions-legislatives/20240613-191520/circonscriptions-legislatives-p10.geojson | GPS latitude/longitude to constituency resolution |
| French public-data platform | https://www.data.gouv.fr | Public dataset hosting and provenance |

## Trust model

CitizenEye should be useful, but it must stay honest.

- A postal code alone can be ambiguous.
- A city can contain several legislative constituencies.
- GPS can be stale, coarse, or taken from work instead of home.
- Public vote participation is not the same as physical attendance.
- Non-voting, abstention, and absence should not be collapsed into one vague label.
- A single “good deputy / bad deputy” score would be misleading and partisan in the wrong way.

So the app confirms inferred locations, shows ambiguity when needed, labels public-scrutin statistics carefully, and keeps official-source context available where it matters.

## Current limitations

- No account or persistent saved deputy yet.
- No backend normalization layer yet; the Android client currently downloads and caches large public archives directly.
- No full vote-detail screen yet.
- No notifications yet.
- GPS currently uses Android's last known location; a fresh one-shot location request is a logical next step.
- Address/street-level disambiguation is still needed when GPS is unavailable or not the user's home location.

## Roadmap

Near-term:

- Persist the selected député after onboarding.
- Add vote-detail screens with official source links, dates, scrutin numbers, group breakdowns, and the user's deputy position.
- Add a small backend importer/normalizer so the mobile app consumes compact APIs instead of parsing large archives.
- Add fresh one-shot location acquisition through the fused location provider.
- Add address/street-level fallback for split communes.

Later:

- Topic tagging for votes.
- Local-impact explanations.
- Notifications for new public votes.
- Deputy interventions, amendments, written questions, and parliamentary activity beyond public votes.
- Shareable civic cards that link back to official records.

## Run locally

Requirements:

- Android Studio / Android SDK.
- JDK 17. On macOS, Android Studio's bundled JBR works.

Build and test:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug
```

Install on an attached Android device:

```bash
export ANDROID_HOME=/Users/romains/Library/Android/sdk
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell monkey -p com.citizeneye -c android.intent.category.LAUNCHER 1
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Repository status

This is an early public MVP. Contributions should preserve the core rule: be citizen-friendly, evidence-based, and non-partisan in party terms. Strong pro-transparency stance is welcome; manipulative scoring and outrage mechanics are not.
