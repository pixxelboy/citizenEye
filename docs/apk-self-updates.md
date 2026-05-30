# CitizenEye APK self-updates

CitizenEye distributes sideload APK updates with GitHub infrastructure only:

- APK binaries live in GitHub Releases.
- The small update manifest lives on GitHub Pages at `https://pixxelboy.github.io/citizenEye/update.json`.
- The Android app checks that manifest on launch and opens the standard Android Package Installer when an update is available.

There is no backend service, Firebase, Google Play dependency, or silent install path.

## Versioning

The canonical Android version is in `gradle.properties`:

```properties
citizeneye.versionCode=1
citizeneye.versionName=0.1.0
```

Rules:

- Increment `citizeneye.versionCode` for every APK that should be offered as an update.
- Keep `citizeneye.versionName` human-readable, for example `1.4.2`.
- The app reads its installed version from Android package metadata at runtime.
- The update checker shows an update only when remote `versionCode` is greater than the installed version code.

## Release pipeline

Workflow: `.github/workflows/android-apk-update-release.yml`

It can be triggered manually from GitHub Actions or by pushing a `v*` tag.

The workflow:

1. Reads `citizeneye.versionCode` and `citizeneye.versionName` from `gradle.properties`.
2. Builds and signs `assembleRelease`.
3. Creates/updates a GitHub Release tagged `v<versionName>`.
4. Uploads:
   - `citizeneye-<versionName>.apk`
   - `citizeneye-<versionName>.apk.sha256`
   - `update.json`
5. Builds the existing GitHub Pages public site.
6. Copies `dist/update.json` to `public/update.json`.
7. Deploys the full Pages artifact, preserving the existing web deployment and static data endpoints.

Required GitHub Actions secrets:

- `CITIZENEYE_RELEASE_KEYSTORE_BASE64`
- `CITIZENEYE_RELEASE_STORE_PASSWORD`
- `CITIZENEYE_RELEASE_KEY_ALIAS`
- `CITIZENEYE_RELEASE_KEY_PASSWORD`

`CITIZENEYE_RELEASE_KEYSTORE_BASE64` is the base64-encoded Android signing keystore.

## update.json shape

Published URL:

`https://pixxelboy.github.io/citizenEye/update.json`

Example:

```json
{
  "versionCode": 12,
  "versionName": "1.4.2",
  "apkUrl": "https://github.com/pixxelboy/citizenEye/releases/download/v1.4.2/citizeneye-1.4.2.apk",
  "releaseNotes": ["Navigation plus claire", "Correctifs de stabilité"],
  "mandatory": false,
  "publishedAt": "2026-05-30T12:00:00Z",
  "sha256": "..."
}
```

Security constraints enforced by the app:

- `update.json` must be fetched over HTTPS.
- `apkUrl` must be HTTPS.
- `versionCode` must be a positive integer and greater than the installed version.
- If `sha256` is present, the downloaded APK checksum must match before Android installer opens.
- The app never installs anything silently.

The normal public-data workflow also tries to preserve the latest Release `update.json` asset whenever it redeploys GitHub Pages, so the daily static-data site refresh does not accidentally remove the update manifest.

## Android app behavior

On launch, the app checks `update.json` if the last check was more than 6 hours ago.

Failure cases are non-blocking:

- no network
- invalid manifest
- missing APK URL
- invalid checksum
- download failure
- user cancels install
- unknown-source permission denied

When an update is available, the app shows:

- title: `Nouvelle version disponible`
- version name
- release notes
- explanation that Android will ask for installation confirmation
- buttons: `Mettre à jour` and `Plus tard`

If `mandatory` is true, the dialog hides `Plus tard`, but the app still avoids a dead-end screen.

When the user taps `Mettre à jour`:

1. The APK downloads into app cache.
2. The checksum is verified when `sha256` is present.
3. A `FileProvider` exposes the APK as a `content://` URI.
4. Android Package Installer opens with MIME type `application/vnd.android.package-archive`.
5. Android asks the user to confirm installation.

If Android requires permission to install unknown apps from CitizenEye, the app opens the relevant Android settings screen. The user can allow it and tap update again.

## Manual release procedure

1. Update `citizeneye.versionCode` and `citizeneye.versionName` in `gradle.properties`.
2. Commit and push to `main`.
3. Trigger `Android APK update release` manually, or create/push tag `v<versionName>`.
4. Wait for the workflow to finish.
5. Verify:
   - GitHub Release exists.
   - APK asset downloads.
   - `https://pixxelboy.github.io/citizenEye/update.json` returns the new manifest.
   - `apkUrl` points to the Release asset.

## Local testing

Unit tests:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest
```

Build smoke test:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Manual QA checklist:

1. Install an older CitizenEye APK.
2. Publish a newer `update.json` with a higher `versionCode`.
3. Launch the old APK.
4. Confirm the update prompt appears.
5. Tap `Mettre à jour`.
6. Confirm the APK downloads.
7. Confirm Android Package Installer opens.
8. Complete installation.
9. Relaunch CitizenEye.
10. Confirm the prompt no longer appears.

## Why silent install is impossible

Normal sideloaded Android apps cannot silently install APK updates. Android intentionally requires user confirmation for package installation, and recent Android versions may require the user to explicitly allow installs from the source app. CitizenEye therefore uses the supported Package Installer flow and never attempts to bypass OS confirmation.
