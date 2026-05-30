#!/usr/bin/env python3
from __future__ import annotations

import argparse
import html
from pathlib import Path
from typing import Iterable

from release_notes_data import RELEASE_NOTES, SECTION_LABELS, ReleaseNote

DESCRIPTION = "Suivez les dernières améliorations de CitizenEye, les mises à jour de données et les prochaines fonctionnalités d’intérêt public."


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def render_section(key: str, items: Iterable[str]) -> str:
    items = list(items)
    if not items:
        return ""
    label = SECTION_LABELS.get(key, key)
    list_items = "\n".join(f"              <li>{esc(item)}</li>" for item in items)
    return f"""
            <section class=\"release-section\">
              <div class=\"badge\">{esc(label)}</div>
              <ul>
{list_items}
              </ul>
            </section>"""


def render_note(note: ReleaseNote) -> str:
    ordered_sections = ["new", "improved", "fixed", "data_sources", "coming_next"]
    sections = "\n".join(render_section(key, note.sections.get(key, [])) for key in ordered_sections).strip()
    section_html = sections or '<p class="empty-inline">Aucune section détaillée pour cette version.</p>'
    return f"""
        <article class=\"release-card\">
          <div class=\"release-meta\"><span>{esc(note.version)}</span><time datetime=\"{esc(note.date)}\">{esc(note.date)}</time></div>
          <h2>{esc(note.title)}</h2>
          <p class=\"release-summary\">{esc(note.summary)}</p>
          <div class=\"release-sections\">
            {section_html}
          </div>
        </article>"""


def render_page() -> str:
    notes = sorted(RELEASE_NOTES, key=lambda note: note.date, reverse=True)
    cards = "\n".join(render_note(note) for note in notes) if notes else '<div class="empty-state">Aucune note de version pour le moment. Les mises à jour de CitizenEye apparaîtront ici.</div>'
    return f"""<!doctype html>
<html lang=\"fr\">
<head>
  <meta charset=\"utf-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
  <meta name=\"description\" content=\"{DESCRIPTION}\">
  <title>Notes de version · CitizenEye</title>
  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">
  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>
  <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;450;500;600;700;800&display=swap\" rel=\"stylesheet\">
  <link rel=\"icon\" type=\"image/svg+xml\" href=\"../assets/brand/citizeneye-eye.svg\">
  <style>
    :root {{
      --ink: #101828; --ink-strong: #06142f; --muted: #526173; --soft: #758195;
      --line: #d9e2ee; --line-soft: #e8edf4; --paper: #fbfaf7; --surface: #ffffff;
      --surface-soft: #f5f7fb; --blue: #153b6f; --blue-strong: #0d2a52; --blue-soft: #e7eef8;
      --green: #256f53; --cream: #fff8e8; --shadow-soft: 0 10px 30px rgba(16,24,40,.08);
      --radius: 24px; --container: 980px;
    }}
    * {{ box-sizing: border-box; }}
    body {{ margin: 0; font-family: Inter, system-ui, -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif; color: var(--ink); background: var(--paper); line-height: 1.55; }}
    body::before {{ content: \"\"; position: fixed; inset: 0; pointer-events: none; background: radial-gradient(circle at 18% 4%, rgba(21,59,111,.12), transparent 28rem), radial-gradient(circle at 86% 12%, rgba(201,63,74,.08), transparent 26rem); z-index: -1; }}
    a {{ color: inherit; text-decoration: none; }}
    h1, h2, h3, p {{ margin-top: 0; }}
    .container {{ width: min(var(--container), calc(100% - 40px)); margin: 0 auto; }}
    .nav {{ position: sticky; top: 0; z-index: 20; border-bottom: 1px solid rgba(217,226,238,.78); background: rgba(251,250,247,.9); backdrop-filter: blur(18px); }}
    .nav-inner {{ min-height: 74px; display: flex; align-items: center; justify-content: space-between; gap: 22px; }}
    .brand {{ display: inline-flex; align-items: center; gap: 11px; color: var(--ink-strong); font-weight: 800; letter-spacing: -.025em; }}
    .mark {{ width: 36px; height: 36px; border-radius: 12px; display: grid; place-items: center; overflow: hidden; background: #07152f; border: 1px solid rgba(21,59,111,.12); }}
    .mark img {{ width: 100%; height: 100%; display: block; }}
    .nav-links {{ display: flex; align-items: center; gap: 20px; color: var(--muted); font-size: 14px; font-weight: 700; }}
    .nav-links a:hover, .footer-col a:hover {{ color: var(--blue); }}
    .btn {{ min-height: 46px; display: inline-flex; align-items: center; justify-content: center; padding: 12px 18px; border-radius: 999px; border: 1px solid transparent; font-size: 15px; font-weight: 800; }}
    .btn.launch {{ color: #fff; background: #0f513f; box-shadow: var(--shadow-soft); }}
    .btn.small {{ min-height: 40px; padding: 10px 15px; font-size: 14px; }}
    .hero {{ padding: 72px 0 44px; }}
    .eyebrow {{ display: inline-flex; align-items: center; gap: 8px; width: max-content; max-width: 100%; margin-bottom: 18px; padding: 7px 11px; border: 1px solid var(--line); border-radius: 999px; background: rgba(255,255,255,.78); color: var(--blue); font-size: 13px; font-weight: 750; }}
    .eyebrow::before {{ content: \"\"; width: 7px; height: 7px; border-radius: 50%; background: var(--green); }}
    h1 {{ max-width: 820px; margin-bottom: 18px; color: var(--ink-strong); font-size: clamp(42px, 6vw, 72px); line-height: 1; letter-spacing: -.064em; font-weight: 800; }}
    .lead {{ max-width: 760px; color: var(--muted); font-size: clamp(18px, 2.1vw, 23px); line-height: 1.55; letter-spacing: -.018em; }}
    .timeline {{ display: grid; gap: 18px; padding: 24px 0 92px; }}
    .release-card {{ position: relative; padding: clamp(24px, 4vw, 38px); border: 1px solid var(--line-soft); border-radius: 30px; background: rgba(255,255,255,.92); box-shadow: var(--shadow-soft); }}
    .release-meta {{ display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; margin-bottom: 16px; color: var(--soft); font-size: 13px; font-weight: 800; text-transform: uppercase; letter-spacing: .04em; }}
    .release-card h2 {{ color: var(--ink-strong); margin-bottom: 10px; font-size: clamp(26px, 3.3vw, 38px); line-height: 1.08; letter-spacing: -.045em; }}
    .release-summary {{ max-width: 760px; color: var(--muted); font-size: 18px; }}
    .release-sections {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 22px; }}
    .release-section {{ padding: 16px; border: 1px solid var(--line-soft); border-radius: 20px; background: var(--surface-soft); }}
    .badge {{ display: inline-flex; margin-bottom: 10px; padding: 5px 9px; border-radius: 999px; background: var(--blue-soft); color: var(--blue); font-size: 12px; font-weight: 900; }}
    ul {{ margin: 0; padding-left: 20px; color: var(--muted); }}
    li + li {{ margin-top: 7px; }}
    .empty-state, .empty-inline {{ padding: 22px; border: 1px solid var(--line-soft); border-radius: 20px; background: var(--surface); color: var(--muted); }}
    footer {{ padding: 62px 0; background: #07152f; color: rgba(255,255,255,.72); }}
    .footer-grid {{ display: grid; grid-template-columns: minmax(260px, 1fr) repeat(3, minmax(150px, .42fr)); gap: 34px; }}
    .footer-brand {{ color: #fff; font-weight: 900; font-size: 18px; }}
    .footer-copy {{ margin-top: 12px; max-width: 390px; font-size: 14px; }}
    .footer-col h3 {{ color: #fff; font-size: 13px; text-transform: uppercase; letter-spacing: .08em; }}
    .footer-col a {{ display: block; margin-top: 10px; font-size: 14px; }}
    .disclaimer {{ margin-top: 44px; padding-top: 24px; border-top: 1px solid rgba(255,255,255,.12); color: rgba(255,255,255,.58); font-size: 13px; }}
    @media (max-width: 1040px) {{ .nav-links a:not(.btn) {{ display: none; }} .footer-grid {{ grid-template-columns: 1fr 1fr; }} }}
    @media (max-width: 640px) {{ .container {{ width: min(100% - 28px, var(--container)); }} .nav-inner {{ min-height: 66px; }} .nav-links {{ gap: 10px; }} .nav-links .btn {{ min-height: 42px; padding: 9px 12px; }} h1 {{ font-size: clamp(34px, 10vw, 42px); line-height: 1.04; letter-spacing: -.056em; }} .lead {{ font-size: 17px; }} .release-sections, .footer-grid {{ grid-template-columns: 1fr; }} .release-card {{ border-radius: 22px; }} }}
  </style>
</head>
<body>
  <nav class=\"nav\" aria-label=\"Navigation principale\">
    <div class=\"container nav-inner\">
      <a class=\"brand\" href=\"../\" aria-label=\"Accueil CitizenEye\"><span class=\"mark\" aria-hidden=\"true\"><img src=\"../assets/brand/citizeneye-eye.svg\" alt=\"\"></span><span>CitizenEye</span></a>
      <div class=\"nav-links\">
        <a href=\"../#comment-ca-marche\">Comment ça marche</a>
        <a href=\"../#decisions-a-venir\">Décisions à venir</a>
        <a href=\"../#votre-depute\">Votre député</a>
        <a href=\"./\">Notes de version</a>
        <a class=\"btn launch small\" href=\"https://github.com/pixxelboy/citizenEye/releases/latest/download/citizeneye-preview.apk\">Lancer</a>
      </div>
    </div>
  </nav>
  <main>
    <header class=\"hero\">
      <div class=\"container\">
        <div class=\"eyebrow\">Mises à jour produit</div>
        <h1>Notes de version</h1>
        <p class=\"lead\">Ce qui change dans CitizenEye, pourquoi cela compte, et ce qui arrive ensuite.</p>
      </div>
    </header>
    <section class=\"container timeline\" aria-label=\"Notes de version CitizenEye\">
      {cards}
    </section>
  </main>
  <footer>
    <div class=\"container footer-grid\">
      <div><div class=\"footer-brand\">CitizenEye</div><p class=\"footer-copy\">Un outil civique indépendant pour comprendre les décisions parlementaires, suivre votre député et agir de manière informée.</p></div>
      <div class=\"footer-col\"><h3>Parcours</h3><a href=\"../#comment-ca-marche\">Comment ça marche</a><a href=\"../#decisions-a-venir\">Décisions à venir</a><a href=\"../#votre-depute\">Votre député</a></div>
      <div class=\"footer-col\"><h3>Projet</h3><a href=\"./\">Notes de version</a><a href=\"../#pourquoi\">Pourquoi CitizenEye</a><a href=\"../manifest.json\">Manifeste des données</a><a href=\"https://github.com/pixxelboy/citizenEye\">Code source</a></div>
      <div class=\"footer-col\"><h3>Soutien</h3><a href=\"https://buymeacoffee.com/pixxelboy\">Soutenir CitizenEye</a><a href=\"https://github.com/pixxelboy/citizenEye/issues\">Signaler un problème</a></div>
    </div>
    <div class=\"container disclaimer\">CitizenEye est indépendant et n’est affilié ni à l’Assemblée nationale, ni à une institution publique, un parti politique, une campagne ou un groupe parlementaire. Les sources officielles restent la référence.</div>
  </footer>
</body>
</html>
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="public/release-notes/index.html")
    args = parser.parse_args()
    output = Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render_page(), encoding="utf-8")
    print(f"wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
