# CitizenEye repository rules for AI agents

These rules are authoritative for work in this repository.

## Language and public copy

CitizenEye is French-first. Android UI, public website copy, public release notes, empty states, labels, CTAs, and user-facing methodology text must be written in French unless the user explicitly asks for another language.

Do not introduce English user-facing strings during normal feature, bugfix, documentation, data, website, or release-note work.

## Release metadata gate

Do not update public release notes or Android app version during normal feature, bugfix, documentation, data, or website work.

Only generate/update release notes and bump the Android app version when the user explicitly asks to merge/release on `main`.

Release metadata files:
- `tools/data/release_notes_data.py`
- `gradle.properties` lines `citizeneye.versionCode` and `citizeneye.versionName`

Normal work:
- Leave release notes unchanged.
- Leave `citizeneye.versionCode` unchanged.
- Leave `citizeneye.versionName` unchanged.

Explicit merge-on-main/release request:
- Add the public, plain-language release note.
- Bump `citizeneye.versionCode` monotonically.
- Bump `citizeneye.versionName` appropriately.
- Verify release/update tests and generated public pages.

Before handoff, run:

```bash
python3 tools/check_release_metadata_gate.py
```

When and only when the user explicitly requested merge/release on `main`, run:

```bash
python3 tools/check_release_metadata_gate.py --allow-release-metadata
```
