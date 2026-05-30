import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from build_release_notes_page import render_page, main
from release_notes_data import RELEASE_NOTES, ReleaseNote


def _text_without_tags(html: str) -> str:
    text = re.sub(r"<style[\s\S]*?</style>", " ", html, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


class ReleaseNotesPageTest(unittest.TestCase):
    def test_release_notes_page_contains_metadata_and_seed_content(self):
        html = render_page()
        text = _text_without_tags(html)

        self.assertIn("<title>Release Notes · CitizenEye</title>", html)
        self.assertIn("Follow the latest CitizenEye improvements, data updates, and upcoming public-interest features.", html)
        self.assertIn("What changed in CitizenEye, why it matters, and what is coming next.", text)
        self.assertIn("clearer public homepage", text.lower())
        self.assertIn("Buy Me a Coffee", text)
        self.assertIn("député", text)
        self.assertIn("political groups", text)
        self.assertIn("dataviz", text)

    def test_release_notes_use_expected_navigation_and_base_path_safe_links(self):
        html = render_page()
        nav = re.search(r"<nav[\s\S]*?</nav>", html, flags=re.IGNORECASE)
        self.assertIsNotNone(nav)
        nav_html = nav.group(0) if nav else ""

        self.assertIn('href="../"', nav_html)
        self.assertIn('href="../#comment-ca-marche"', nav_html)
        self.assertIn('href="./"', nav_html)
        self.assertIn('../assets/brand/citizeneye-eye.svg', nav_html)
        self.assertNotIn('lottie-player', nav_html)

    def test_release_notes_empty_state_does_not_crash(self):
        with patch("build_release_notes_page.RELEASE_NOTES", []):
            html = render_page()

        self.assertIn("No release notes yet. CitizenEye updates will appear here soon.", html)

    def test_release_notes_are_reverse_chronological(self):
        notes = [
            ReleaseNote(version="Old", date="2026-01-01", title="Old title", summary="Old summary"),
            ReleaseNote(version="New", date="2026-03-01", title="New title", summary="New summary"),
        ]
        with patch("build_release_notes_page.RELEASE_NOTES", notes):
            html = render_page()

        self.assertLess(html.index("New title"), html.index("Old title"))

    def test_generator_writes_index_for_extensionless_pages_route(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "release-notes" / "index.html"
            with patch("sys.argv", ["build_release_notes_page.py", "--out", str(out)]):
                self.assertEqual(main(), 0)
            self.assertTrue(out.exists())
            self.assertIn("Release Notes · CitizenEye", out.read_text(encoding="utf-8"))

    def test_release_notes_data_is_structured(self):
        self.assertGreaterEqual(len(RELEASE_NOTES), 1)
        first = RELEASE_NOTES[0]
        self.assertTrue(first.version)
        self.assertRegex(first.date, r"^\d{4}-\d{2}-\d{2}$")
        self.assertTrue(first.title)
        self.assertTrue(first.summary)
        self.assertIn("coming_next", first.sections)


if __name__ == "__main__":
    unittest.main()
