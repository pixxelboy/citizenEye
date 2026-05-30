#!/usr/bin/env python3
"""Guard CitizenEye release metadata edits.

Default mode is for normal feature/bugfix/docs/data work: release notes and
Android version fields must stay untouched.

Use --allow-release-metadata only when the user explicitly asked to merge or
release on main. In that mode, release notes and Android version should move
together.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

RELEASE_NOTES_PATH = "tools/data/release_notes_data.py"
VERSION_PATH = "gradle.properties"
VERSION_PREFIXES = ("citizeneye.versionCode=", "citizeneye.versionName=")


def run_git(args: list[str]) -> str:
    result = subprocess.run(
        ["git", *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


def changed_files() -> set[str]:
    names = set()
    # HEAD covers both staged and unstaged changes in this working tree.
    for line in run_git(["diff", "--name-only", "HEAD", "--"]).splitlines():
        if line.strip():
            names.add(line.strip())
    for line in run_git(["ls-files", "--others", "--exclude-standard"]).splitlines():
        if line.strip():
            names.add(line.strip())
    return names


def version_fields_changed() -> bool:
    if VERSION_PATH not in changed_files():
        return False
    diff = run_git(["diff", "HEAD", "--", VERSION_PATH])
    return any(
        line.startswith(("+", "-")) and not line.startswith(("+++", "---")) and line[1:].startswith(VERSION_PREFIXES)
        for line in diff.splitlines()
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--allow-release-metadata",
        action="store_true",
        help="Allow release notes/version edits for an explicit merge-on-main or release request.",
    )
    args = parser.parse_args()

    files = changed_files()
    release_notes_changed = RELEASE_NOTES_PATH in files
    version_changed = version_fields_changed()

    if not args.allow_release_metadata:
        violations = []
        if release_notes_changed:
            violations.append(RELEASE_NOTES_PATH)
        if version_changed:
            violations.append(f"{VERSION_PATH} version fields")
        if violations:
            print(
                "Release metadata changed during normal work. Only update release notes and app version "
                "when the user explicitly asks to merge/release on main.",
                file=sys.stderr,
            )
            for item in violations:
                print(f"- {item}", file=sys.stderr)
            return 1
        print("Release metadata gate passed: no release notes or app version edits.")
        return 0

    if release_notes_changed != version_changed:
        print(
            "Allowed release metadata mode requires release notes and Android version fields to change together.",
            file=sys.stderr,
        )
        print(f"- release notes changed: {release_notes_changed}", file=sys.stderr)
        print(f"- Android version fields changed: {version_changed}", file=sys.stderr)
        return 1

    if release_notes_changed and version_changed:
        print("Release metadata gate passed: release notes and app version changed together.")
    else:
        print("Release metadata gate passed: no release metadata changes detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
