# Ingestion des données CIVIX

## Objectif

CitizenEye reste une application frontend/static-data first. L'ingestion CIVIX récupère des données parlementaires publiques au moment de la génération du jeu statique, puis écrit des fichiers JSON locaux sous `public/data/civix/`.

L'application ne doit pas appeler l'API CIVIX à l'exécution. Les futurs écrans ou dataviz doivent consommer uniquement les fichiers JSON générés.

## Sources

- Page API CIVIX : https://www.civix.fr/api
- Métadonnées machine-readable : https://www.civix.fr/.well-known/civix.json
- OpenAPI CIVIX : https://www.civix.fr/.well-known/openapi.json
- Listing data.gouv.fr : https://www.data.gouv.fr/dataservices/api-publique-civix
- Site CIVIX : https://www.civix.fr

CIVIX présente des données publiques de l'Assemblée nationale. Le listing data.gouv.fr indique une mise à jour au 1er avril 2026. L'API est publique/read-only, avec un usage raisonnable recommandé.

## Fichiers générés

La commande d'ingestion écrit :

- `public/data/civix/deputies.json` : députés normalisés, identifiants CIVIX et identifiants stables CitizenEye.
- `public/data/civix/groups.json` : groupes parlementaires.
- `public/data/civix/scrutins.json` : scrutins publics et références aux dossiers/textes quand elles sont exposées.
- `public/data/civix/votes.json` : votes individuels récupérés pour les scrutins configurés.
- `public/data/civix/dossiers.json` : dossiers législatifs dérivés des références de scrutins disponibles dans l'API actuelle.
- `public/data/civix/stats.json` : statistiques déterministes dérivées du jeu généré.
- `public/data/civix/index.json` : index des fichiers et métadonnées CIVIX.
- `public/data/civix/ingestion-report.json` : statut de chaque endpoint, volumes récupérés, éléments ignorés et erreurs non critiques.

Chaque fichier métier contient au minimum :

- `schemaVersion`
- `dataset`
- `generatedAt`
- `civixFetchedAt` quand CIVIX l'expose
- `civixApiVersion` quand disponible
- `source`
- le tableau ou objet de données normalisé

## Commandes locales

Exécuter les tests de l'ingestion :

```bash
python3 -m unittest tools.data.test_build_civix_dataset -v
```

Générer le jeu CIVIX :

```bash
python3 tools/data/build_civix_dataset.py --out public/data/civix
```

Options utiles :

```bash
# Limiter les appels nominaux /scrutins/{uid}/votes pour usage raisonnable
CIVIX_MAX_SCRUTIN_VOTES=250 python3 tools/data/build_civix_dataset.py --out public/data/civix

# Récupérer les votes nominaux pour tous les scrutins, seulement si l'usage CIVIX reste acceptable
python3 tools/data/build_civix_dataset.py --out public/data/civix --max-scrutin-votes -1
```

## GitHub Actions

Le workflow `.github/workflows/build-dataset.yml` :

- s'exécute tous les jours ;
- peut être lancé manuellement avec `workflow_dispatch` ;
- se relance sur les changements du script, du test, de la documentation ou du workflow ;
- installe Python ;
- exécute les tests d'ingestion ;
- génère `public/data/civix/*.json` ;
- valide la présence et le JSON des fichiers ;
- vérifie que les endpoints critiques CIVIX ne sont pas en échec ;
- commit les fichiers générés uniquement si Git détecte un changement.

Le workflow évite les commits bruyants quand les données ne changent pas.

## Couche d'accès app

La classe Kotlin `CivixStaticDataset` lit des fichiers JSON locaux depuis un répertoire fourni et expose :

- `getCivixDeputies()`
- `getCivixGroups()`
- `getCivixScrutins()`
- `getCivixVotes()`
- `getCivixDossiers()`
- `getCivixStats()`
- `getDeputyCivixProfile(deputyId)`
- `getVotesForDeputy(deputyId)`
- `getVotesForScrutin(scrutinId)`
- `getGroupStats(groupId)`

Cette couche ne contient aucun appel réseau.

## Limites des données

- Les champs absents dans CIVIX restent `null` ou vides ; CitizenEye ne les invente pas.
- Les identifiants CIVIX originaux sont conservés.
- Les identifiants CitizenEye stables sont ajoutés seulement quand ils sont déterministes.
- Dans l'OpenAPI CIVIX actuelle, aucun endpoint dossier dédié n'est exposé. `dossiers.json` est donc construit à partir des références de dossiers présentes dans les scrutins.
- Les votes individuels sont récupérés via `/api/v1/scrutins/{uid}/votes`. Par défaut, l'ingestion limite le nombre de scrutins interrogés pour respecter l'usage raisonnable ; la limite est consignée dans `ingestion-report.json` et configurable.
- Si un endpoint non critique échoue, l'ingestion continue et le rapport indique l'échec.

## Règle de neutralité

CitizenEye affiche uniquement des faits parlementaires sourcés. L'application ne déduit pas d'opinions politiques, ne produit pas de scoring partisan, n'ajoute pas d'étiquettes idéologiques non sourcées et ne classe pas les députés selon une opinion inférée.
