from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List

ReleaseSectionName = str

SECTION_LABELS: Dict[ReleaseSectionName, str] = {
    "new": "New",
    "improved": "Improved",
    "fixed": "Fixed",
    "data_sources": "Data & Sources",
    "coming_next": "Coming next",
}


@dataclass(frozen=True)
class ReleaseNote:
    version: str
    date: str
    title: str
    summary: str
    sections: Dict[ReleaseSectionName, List[str]] = field(default_factory=dict)


RELEASE_NOTES: List[ReleaseNote] = [
    ReleaseNote(
        version="Release notes policy",
        date="2026-05-30",
        title="Release notes are now part of every repository update",
        summary=(
            "Every CitizenEye repository change must now include a public, plain-language release note "
            "so readers can understand what changed without reading technical commits."
        ),
        sections={
            "new": [
                "Release notes are now a required part of updates to the Android app, public website, workflows, documentation, data tooling, and other repository areas.",
                "Each note should explain what changed, where it changed, why it matters, and link to GitHub commits or files when useful.",
            ],
            "improved": [
                "Future public updates will be easier to follow from the Release Notes page instead of being hidden in developer-oriented commit history.",
            ],
            "data_sources": [
                "The release notes source remains a simple structured local file in tools/data/release_notes_data.py, so the public site stays static and does not depend on runtime GitHub API calls.",
            ],
            "coming_next": [
                "Upcoming repository changes will add their own user-facing release note at the same time as the code, page, data, or documentation change.",
            ],
        },
    ),
    ReleaseNote(
        version="Public update",
        date="2026-05-30",
        title="A clearer public home for CitizenEye",
        summary=(
            "The public site now explains CitizenEye faster: what the app does, why it matters, "
            "and how citizens can use it to follow parliamentary activity."
        ),
        sections={
            "new": [
                "A dedicated release notes page so anyone can follow visible product progress without reading technical commits.",
                "A clearer public homepage structure focused on upcoming decisions, députés, and citizen action.",
            ],
            "improved": [
                "Navigation is easier to scan, with clearer paths to the app, upcoming decisions, député information, and project updates.",
                "The Buy Me a Coffee support action is easier to find while staying separate from the main app launch action.",
            ],
            "data_sources": [
                "CitizenEye continues to rely on public parliamentary sources and keeps source visibility as a product principle.",
            ],
            "coming_next": [
                "Richer député pages with votes, political groups, biographies, and better visual summaries.",
                "More useful dataviz to understand participation, voting context, and group positions without partisan scoring.",
            ],
        },
    )
]
