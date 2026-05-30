import re
import unittest
from pathlib import Path

HTML_PATH = Path(__file__).with_name("public-index.html")


def _html() -> str:
    return HTML_PATH.read_text(encoding="utf-8")


def _text_without_tags(html: str) -> str:
    text = re.sub(r"<script[\s\S]*?</script>", " ", html, flags=re.IGNORECASE)
    text = re.sub(r"<style[\s\S]*?</style>", " ", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


class PublicLandingPageTest(unittest.TestCase):
    def test_public_landing_page_is_french_first_and_narrative(self):
        html = _html()
        text = _text_without_tags(html)

        self.assertIn('<html lang="fr">', html)
        self.assertIn("Comprendre les décisions politiques avant qu’elles ne soient prises.", text)
        self.assertIn("CitizenEye vous aide à suivre les décisions parlementaires à venir à l’Assemblée nationale", text)
        self.assertIn("La plupart des citoyens découvrent les décisions importantes une fois qu’elles sont déjà prises.", text)
        self.assertIn("CitizenEye rend l’activité parlementaire compréhensible.", text)
        self.assertIn("Savoir ce qui arrive ensuite.", text)
        self.assertIn("Tout savoir sur votre député au même endroit.", text)
        self.assertIn("Quand un sujet compte, contactez votre représentant.", text)
        self.assertIn("Pourquoi nous avons construit CitizenEye", text)
        self.assertIn("Restez informé avant les décisions importantes.", text)

    def test_public_navigation_uses_outcome_labels_not_internal_structure(self):
        html = _html()
        match = re.search(r"<nav[\s\S]*?</nav>", html, flags=re.IGNORECASE)
        self.assertIsNotNone(match)
        nav = match.group(0) if match else ""

        expected_order = [
            "Comment ça marche",
            "Décisions à venir",
            "Votre député",
            "Pourquoi CitizenEye",
            "Lancer l’application",
        ]
        positions = [nav.index(label) for label in expected_order]
        self.assertEqual(positions, sorted(positions))

        for forbidden in ["Product", "Data", "Download", "Open source", "GitHub", "Neutrality", "Transparency"]:
            self.assertNotIn(forbidden, nav)

    def test_public_landing_page_does_not_use_english_marketing_copy(self):
        text = _text_without_tags(_html())

        forbidden_phrases = [
            "Know how your representative votes",
            "Independent civic technology",
            "Download Android preview",
            "Explore the project",
            "View source code",
            "Public Data",
            "How it works",
            "Built on public parliamentary data",
        ]
        for phrase in forbidden_phrases:
            self.assertNotIn(phrase, text)

    def test_public_landing_page_scope_stays_public_homepage_only(self):
        html = _html()

        self.assertIn("href=\"#comment-ca-marche\"", html)
        self.assertIn("href=\"#decisions-a-venir\"", html)
        self.assertIn("href=\"#votre-depute\"", html)
        self.assertIn("href=\"#pourquoi\"", html)
        self.assertIn("href=\"https://github.com/pixxelboy/citizenEye/releases/latest/download/citizeneye-preview.apk\"", html)

        for forbidden_href in ["/dashboard", "/search", "/votes", "/deputies", "/deputes", "/app/"]:
            self.assertNotIn(forbidden_href, html)


if __name__ == "__main__":
    unittest.main()
