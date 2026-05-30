from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List

ReleaseSectionName = str

SECTION_LABELS: Dict[ReleaseSectionName, str] = {
    "new": "Nouveautés",
    "improved": "Améliorations",
    "fixed": "Corrections",
    "data_sources": "Données et sources",
    "coming_next": "À venir",
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
        version="0.1.1",
        date="2026-05-30",
        title="Des sujets de vote plus lisibles pour chaque député",
        summary=(
            "CitizenEye regroupe désormais les votes publics par grands sujets politiques à partir des dossiers législatifs officiels, "
            "sans notation politique ni classification par IA."
        ),
        sections={
            "new": [
                "L’écran d’accueil affiche les sujets les plus votés par le député sélectionné, avec le nombre de votes et la répartition Pour, Contre et Abstention.",
                "Le site public présente la méthode de classement déterministe fondée sur les dossiers législatifs de l’Assemblée nationale.",
            ],
            "improved": [
                "Les citoyens peuvent comprendre plus vite les thèmes réellement débattus sans lire chaque amendement ou intitulé parlementaire technique.",
                "Les libellés restent descriptifs, par exemple Majoritairement pour, Majoritairement contre ou Mixte, sans déduire de position idéologique.",
            ],
            "data_sources": [
                "Les sujets sont attribués à partir des titres de dossiers législatifs officiels, puis hérités par les votes liés au même dossier.",
                "La classification est locale, déterministe et explicable : aucun LLM, aucune API externe et aucun score politique ne sont utilisés.",
            ],
            "coming_next": [
                "Les prochains ajustements amélioreront la validation publique des mots-clés et des dossiers associés, tout en gardant une méthode reproductible.",
            ],
        },
    ),
    ReleaseNote(
        version="Règle des notes de version",
        date="2026-05-30",
        title="Les notes de version font désormais partie du suivi public",
        summary=(
            "Les mises à jour de CitizenEye doivent être expliquées en français clair, "
            "afin que chacun puisse comprendre ce qui change sans lire les commits techniques."
        ),
        sections={
            "new": [
                "Les notes de version couvrent les évolutions visibles de l’application Android, du site public, des workflows, de la documentation et des outils de données.",
                "Chaque note doit expliquer ce qui change, où le changement apparaît, pourquoi il compte pour les citoyens, et pointer vers GitHub lorsque c’est utile.",
            ],
            "improved": [
                "Les prochaines mises à jour publiques seront plus faciles à suivre depuis la page Notes de version, sans dépendre de l’historique technique des commits.",
            ],
            "data_sources": [
                "La source des notes de version reste un fichier structuré local dans tools/data/release_notes_data.py, afin que le site public reste statique et indépendant des API GitHub au moment de l’affichage.",
            ],
            "coming_next": [
                "Les prochaines évolutions visibles ajouteront une note de version utilisateur au moment de la mise à jour publique ou de la publication demandée.",
            ],
        },
    ),
    ReleaseNote(
        version="Mise à jour publique",
        date="2026-05-30",
        title="Une page d’accueil plus claire pour CitizenEye",
        summary=(
            "Le site public explique plus rapidement ce que fait CitizenEye, pourquoi c’est utile, "
            "et comment suivre l’activité parlementaire de son député."
        ),
        sections={
            "new": [
                "Une page Notes de version dédiée permet de suivre les progrès visibles du produit sans lire les commits techniques.",
                "La page d’accueil publique présente plus clairement les décisions à venir, les députés et les actions citoyennes possibles.",
            ],
            "improved": [
                "La navigation est plus facile à parcourir, avec des accès plus clairs vers l’application, les décisions à venir, les informations sur les députés et les mises à jour du projet.",
                "L’action de soutien Buy Me a Coffee est plus visible tout en restant séparée de l’action principale de lancement de l’application.",
            ],
            "data_sources": [
                "CitizenEye continue de s’appuyer sur des sources parlementaires publiques et garde la visibilité des sources comme principe produit.",
            ],
            "coming_next": [
                "Des pages député plus riches avec votes, groupes politiques, biographies et meilleurs résumés visuels.",
                "Des visualisations plus utiles pour comprendre la participation, le contexte des votes et les positions de groupe sans notation politique.",
            ],
        },
    )
]
