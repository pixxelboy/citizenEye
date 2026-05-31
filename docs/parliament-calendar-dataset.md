# Dataset calendrier parlementaire

## But

CitizenEye reste une application frontend + données statiques. Ce dataset ajoute des dates parlementaires à venir sans backend et sans appel réseau depuis l’UI Android.

Le fichier généré permet à l’écran `À suivre` d’afficher des textes ou événements parlementaires avec une date officielle explicite, quand cette date existe dans une source publique officielle.

## Sources utilisées

Source critique actuelle :

- Assemblée nationale — agenda open data des réunions et séances :
  `https://data.assemblee-nationale.fr/static/openData/repository/17/vp/reunions/Agenda.json.zip`

Le script privilégie les sources officielles Assemblée nationale / Sénat. CIVIX n’est pas utilisé comme source faisant autorité pour les dates à venir : CIVIX peut enrichir les données parlementaires, mais les dates affichées doivent venir d’un agenda ou d’un dossier officiel public.

## Fichiers générés

Les fichiers sont générés dans :

- `public/data/parliament/calendar.json`
- `public/data/parliament/index.json`
- `public/data/parliament/ingestion-report.json`

`calendar.json` contient uniquement des événements datés aujourd’hui ou dans le futur, dans une fenêtre compacte de 90 jours, avec un maximum de 300 éléments.

`index.json` contient un résumé compact : date de génération, nombre d’items, prochaine date, sources et nombre d’avertissements.

`ingestion-report.json` trace chaque endpoint : succès, échecs, items récupérés, items ignorés, erreurs et avertissements.

## Schéma normalisé

Chaque élément de `calendar.json.items` suit cette forme :

```json
{
  "id": "stable deterministic id",
  "source": "assemblee",
  "sourceUid": "...",
  "title": "...",
  "shortTitle": "...",
  "eventDate": "YYYY-MM-DD",
  "eventDateTime": "ISO-8601 or null",
  "dateLabel": "libellé officiel lisible",
  "eventType": "public_session | committee | vote | debate | hearing | other",
  "stage": "...",
  "chamber": "Assemblée nationale",
  "dossierRef": "... or null",
  "legislature": "17",
  "officialUrl": "...",
  "dossierUrl": "... or null",
  "sourceUrls": ["..."],
  "retrievedAt": "ISO-8601",
  "confidence": "explicit_date",
  "raw": {}
}
```

Les IDs sont déterministes à partir de la source, de l’UID source, de la date, du type d’événement et du titre normalisé.

## Règles de sécurité des données

- Pas de backend.
- Pas de secrets.
- Pas d’appel CIVIX ou Assemblée depuis l’UI Android pour ce calendrier.
- Pas de classification par LLM.
- Pas de score d’urgence ou d’opinion.
- Pas d’inférence politique.
- Pas de date fabriquée.
- Les dates vagues comme “prochainement” sont ignorées.
- Les éléments sans date explicite sont exclus de `calendar.json`.
- Chaque élément affichable conserve au moins une URL de source officielle.
- Les éléments invalides sont ignorés ou font échouer la validation selon la gravité, et sont tracés dans le rapport.
- Si la source officielle critique est indisponible, le script sort avec un code non nul.

Règle de neutralité : CitizenEye affiche des faits parlementaires sourcés et n’infère pas d’opinions politiques.

## Commandes locales

Lancer les tests :

```bash
python3 -m unittest tools.data.test_build_parliament_calendar_dataset -v
```

Générer le dataset :

```bash
python3 tools/data/build_parliament_calendar_dataset.py --out public/data/parliament
```

Valider rapidement les fichiers :

```bash
python3 - <<'PY'
import json
from pathlib import Path
root = Path('public/data/parliament')
for name in ['calendar', 'index', 'ingestion-report']:
    path = root / f'{name}.json'
    json.loads(path.read_text(encoding='utf-8'))
    print(path, 'ok')
PY
```

## GitHub Actions

`.github/workflows/build-dataset.yml` :

- tourne chaque jour ;
- peut être déclenché manuellement ;
- tourne sur les changements de scripts/docs/données pertinents ;
- exécute les tests CIVIX et calendrier parlementaire ;
- génère `public/data/civix/*.json` et `public/data/parliament/*.json` ;
- valide les JSON générés ;
- commit uniquement si les fichiers ont changé.

## Limites

Le dataset dépend de la disponibilité et de la structure des sources officielles. Si l’Assemblée ou le Sénat modifie un format, le script doit ignorer les éléments invalides, produire un rapport clair, et échouer uniquement quand une source critique empêche de régénérer un calendrier fiable.

Le calendrier est volontairement compact : événements à venir seulement, fenêtre courte, brut minimal dans `raw` pour auditabilité sans gonfler le fichier.
