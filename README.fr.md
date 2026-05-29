# CitizenEye

CitizenEye est une application Android pensée pour les citoyennes et citoyens français qui veulent comprendre ce que fait leur député à l’Assemblée nationale.

L’application assume une position politique simple : le pouvoir public doit être vérifiable par tout le monde. CitizenEye n’est lié à aucun parti, campagne ou groupe parlementaire. Son but est de rendre les votes, les représentants et les données publiques lisibles sans transformer la démocratie en fil d’indignation.

[English version](README.md)

## Captures d’écran

<p align="center">
  <img src="docs/screenshots/onboarding.svg" width="30%" alt="Accueil CitizenEye avec confirmation de circonscription par GPS" />
  <img src="docs/screenshots/home.svg" width="30%" alt="Accueil CitizenEye avec carte du député et votes publics" />
  <img src="docs/screenshots/profile.svg" width="30%" alt="Profil statistique CitizenEye" />
</p>

## Pourquoi ce projet existe

La plupart des citoyens ne savent pas toujours qui les représente, comment cette personne vote, ni où vérifier l’information officielle. Les données existent, mais elles sont dispersées entre portails publics, archives volumineuses, pages institutionnelles et identifiants administratifs.

CitizenEye transforme cela en parcours simple :

1. Entrer un code postal ou utiliser la géolocalisation.
2. Confirmer la commune, le département et la circonscription détectés.
3. Voir le député qui représente cette localisation.
4. Suivre les derniers scrutins publics et la position enregistrée du député.
5. Ouvrir un profil statistique clair, calculé à partir des données publiques officielles.

L’objectif n’est pas de dire aux citoyens quoi penser. L’objectif est de rendre plus difficile le fait de cacher l’action publique derrière la complexité.

## Ce qui fonctionne aujourd’hui

CitizenEye est déjà un MVP Android connecté à de vraies données publiques, pas une démo avec données fictives.

- Application Android native en Kotlin + Jetpack Compose.
- Recherche par code postal ou ville via les données officielles des communes.
- Autocomplétion optionnelle par géolocalisation.
- Détection GPS de la circonscription par point-in-polygon sur géométrie publique.
- Confirmation explicite avant validation d’une localisation.
- Gestion des cas ambigus quand une ville ou un code postal couvre plusieurs circonscriptions.
- Chargement des députés en exercice depuis l’Open Data de l’Assemblée nationale.
- Portraits officiels arrondis des députés.
- Scrutins publics de la législature en cours depuis les données de l’Assemblée nationale.
- Fil de votes progressif, 20 scrutins à la fois.
- Profil statistique du député :
  - participation aux scrutins publics ;
  - répartition Pour / Contre / Abstention / Non-votant ;
  - avertissement clair : ce ne sont pas des notes de performance.
- Cache local quotidien pour les grands jeux de données publics.
- Repli sur cache existant si une source publique est temporairement indisponible.
- Tests unitaires pour la recherche, le cache, les portraits, la pagination des votes, les statistiques et la géométrie des circonscriptions.

## Sources open data

CitizenEye repose sur des données d’intérêt public. Les sources principales sont :

| Donnée | Source | Utilisation |
| --- | --- | --- |
| Commune, code postal, département, métadonnées INSEE | https://geo.api.gouv.fr | Parcours d’entrée par ville/code postal et libellés |
| Documentation de l’API administrative française | https://geo.api.gouv.fr/decoupage-administratif | Référence pour les recherches commune/département |
| Portail Open Data de l’Assemblée nationale | https://data.assemblee-nationale.fr | Députés, mandats, organes, scrutins publics |
| Députés actifs / mandats / organes | https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip | Identité du député, groupe, département, circonscription |
| Scrutins et positions individuelles | https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip | Votes publics et position enregistrée de chaque député |
| Contours des circonscriptions législatives | https://static.data.gouv.fr/resources/contours-geographiques-des-circonscriptions-legislatives/20240613-191520/circonscriptions-legislatives-p10.geojson | Résolution GPS latitude/longitude vers circonscription |
| Plateforme française des données publiques | https://www.data.gouv.fr | Hébergement et provenance des jeux de données publics |

## Modèle de confiance

CitizenEye doit être utile, mais doit rester honnête.

- Un code postal seul peut être ambigu.
- Une ville peut contenir plusieurs circonscriptions législatives.
- Le GPS peut être ancien, approximatif ou correspondre au lieu de travail plutôt qu’au domicile.
- La participation aux scrutins publics n’est pas la présence physique dans l’hémicycle.
- Non-votant, abstention et absence ne doivent pas être mélangés dans une catégorie floue.
- Une note unique de type “bon député / mauvais député” serait trompeuse et partisane au mauvais sens du terme.

L’application confirme donc les localisations déduites, montre l’ambiguïté quand elle existe, nomme précisément les statistiques de scrutins publics et garde le lien avec les sources officielles là où il est utile.

## Limites actuelles

- Pas encore de compte utilisateur ni de député sauvegardé durablement.
- Pas encore de couche backend de normalisation ; le client Android télécharge et met en cache de grandes archives publiques directement.
- Pas encore d’écran de détail complet pour chaque scrutin.
- Pas encore de notifications.
- Le GPS utilise actuellement la dernière position connue par Android ; une demande fraîche de localisation est la prochaine amélioration logique.
- La désambiguïsation adresse/rue reste nécessaire lorsque le GPS n’est pas disponible ou ne correspond pas au domicile.

## Feuille de route

Court terme :

- Sauvegarder le député sélectionné après l’entrée utilisateur.
- Ajouter les écrans de détail de vote avec liens officiels, dates, numéros de scrutin, ventilation par groupe et position du député.
- Ajouter un petit backend d’import/normalisation pour que l’app mobile consomme des API compactes au lieu de parser de grandes archives.
- Ajouter une acquisition fraîche de localisation via le fused location provider.
- Ajouter un repli adresse/rue pour les communes découpées entre plusieurs circonscriptions.

Plus tard :

- Classement thématique des votes.
- Explications d’impact local.
- Notifications lors de nouveaux scrutins publics.
- Interventions, amendements, questions écrites et activité parlementaire au-delà des seuls votes publics.
- Cartes citoyennes partageables avec retour vers les sources officielles.

## Lancer le projet localement

Prérequis :

- Android Studio / Android SDK.
- JDK 17. Sur macOS, le JBR inclus dans Android Studio fonctionne.

Construire et tester :

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug
```

Installer sur un appareil Android connecté :

```bash
export ANDROID_HOME=/Users/romains/Library/Android/sdk
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell monkey -p com.citizeneye -c android.intent.category.LAUNCHER 1
```

APK debug :

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Statut du dépôt

C’est un MVP public précoce. Toute contribution doit respecter la règle centrale : être utile aux citoyens, fondée sur des preuves, et non partisane au sens des partis politiques. Une position forte pour la transparence est bienvenue ; les mécaniques d’indignation et les scores manipulateurs ne le sont pas.
