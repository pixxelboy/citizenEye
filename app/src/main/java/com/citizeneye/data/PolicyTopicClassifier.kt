package com.citizeneye.data

import java.text.Normalizer

enum class PolicyTopic(val label: String) {
    BUDGET("Budget"),
    HEALTH("Santé"),
    EDUCATION("Éducation"),
    HOUSING("Logement"),
    EMPLOYMENT("Emploi"),
    ECONOMY("Économie"),
    ENVIRONMENT("Environnement"),
    ENERGY("Énergie"),
    TRANSPORT("Transport"),
    SECURITY("Sécurité"),
    JUSTICE("Justice"),
    IMMIGRATION("Immigration"),
    EUROPE("Europe"),
    AGRICULTURE("Agriculture"),
    DIGITAL("Numérique"),
    INSTITUTIONS("Institutions"),
    FOREIGN_AFFAIRS("Affaires étrangères"),
    OTHER("Autre")
}

data class TopicClassification(
    val primaryTopic: PolicyTopic,
    val secondaryTopics: List<PolicyTopic> = emptyList(),
    val matchedKeywords: List<String> = emptyList()
) {
    val allTopics: List<PolicyTopic> get() = listOf(primaryTopic) + secondaryTopics
}

data class TopicVotingSummary(
    val topic: PolicyTopic,
    val totalVotes: Int,
    val pour: Int,
    val contre: Int,
    val abstention: Int,
    val nonVotant: Int
) {
    val dominantPositionLabel: String
        get() = when {
            totalVotes == 0 -> "Mixte"
            pour.toDouble() / totalVotes.toDouble() > 0.60 -> "Majoritairement pour"
            contre.toDouble() / totalVotes.toDouble() > 0.60 -> "Majoritairement contre"
            else -> "Mixte"
        }
}

class PolicyTopicClassifier {
    fun classify(
        dossierTitle: String?,
        dossierTitleNormalized: String? = dossierTitle?.normalizeForTopicMatching(),
        legislativeReference: String? = null,
        objectTitle: String? = null
    ): TopicClassification {
        val searchableParts = listOfNotNull(
            dossierTitleNormalized,
            dossierTitle,
            legislativeReference
        )
        val searchableText = searchableParts
            .joinToString(" ")
            .normalizeForTopicMatching()
            .trim()

        if (searchableText.isBlank()) return TopicClassification(PolicyTopic.OTHER)

        val matches = TOPIC_MATCHERS.mapNotNull { matcher ->
            val index = matcher.regex.find(searchableText)?.range?.first ?: -1
            if (index >= 0) TopicKeywordMatch(matcher.topic, matcher.keyword, index) else null
        }.sortedWith(compareBy<TopicKeywordMatch> { it.index }.thenBy { it.topic.ordinal })

        if (matches.isEmpty()) return TopicClassification(PolicyTopic.OTHER)

        val topics = matches.map { it.topic }.distinct()
        return TopicClassification(
            primaryTopic = topics.first(),
            secondaryTopics = topics.drop(1),
            matchedKeywords = matches.map { it.keyword }.distinct()
        )
    }

    private data class TopicKeywordMatch(
        val topic: PolicyTopic,
        val keyword: String,
        val index: Int
    )

    private data class TopicKeywordMatcher(
        val topic: PolicyTopic,
        val keyword: String,
        val regex: Regex
    )

    companion object {
        private const val WORD_BOUNDARY = "[\\p{L}\\p{N}]"
        private val TOPIC_KEYWORDS: Map<PolicyTopic, List<String>> = linkedMapOf(
            PolicyTopic.BUDGET to listOf(
                "loi de finances", "budget", "financement", "fiscalité", "fiscalite", "impôt", "impot", "taxe", "prélèvement", "prelevement", "recettes", "dépenses", "depenses", "sécurité sociale", "securite sociale"
            ),
            PolicyTopic.HEALTH to listOf(
                "santé", "sante", "hôpital", "hopital", "médical", "medical", "médecin", "medecin", "assurance maladie", "soins", "patient", "pharmacie", "médicament", "medicament", "prévention", "prevention"
            ),
            PolicyTopic.EDUCATION to listOf(
                "éducation", "education", "école", "ecole", "enseignement", "université", "universite", "étudiant", "etudiant", "professeur", "enseignant", "apprentissage", "formation professionnelle", "lycée", "lycee", "collège", "college"
            ),
            PolicyTopic.HOUSING to listOf(
                "logement", "habitat", "immobilier", "locataire", "bailleur", "loyer", "copropriété", "copropriete", "urbanisme", "hébergement", "hebergement", "rénovation énergétique", "renovation energetique"
            ),
            PolicyTopic.EMPLOYMENT to listOf(
                "emploi", "travail", "chômage", "chomage", "salarié", "salarie", "contrat de travail", "retraite", "pension", "formation professionnelle", "apprentissage", "conditions de travail", "dialogue social"
            ),
            PolicyTopic.ECONOMY to listOf(
                "économie", "economie", "entreprise", "industrie", "commerce", "pouvoir d'achat", "pouvoir d’achat", "consommation", "concurrence", "artisanat", "pme", "tpe", "marché", "marche", "croissance"
            ),
            PolicyTopic.ENVIRONMENT to listOf(
                "climat", "biodiversité", "biodiversite", "écologie", "ecologie", "environnement", "pollution", "eau", "déchets", "dechets", "nature", "forêt", "foret", "transition écologique", "transition ecologique"
            ),
            PolicyTopic.ENERGY to listOf(
                "énergie", "energie", "énergétique", "energetique", "nucléaire", "nucleaire", "électricité", "electricite", "gaz", "renouvelable", "transition énergétique", "transition energetique", "hydrogène", "hydrogene", "carburant", "chauffage"
            ),
            PolicyTopic.TRANSPORT to listOf(
                "transport", "mobilité", "mobilite", "ferroviaire", "train", "route", "autoroute", "véhicule", "vehicule", "automobile", "aérien", "aerien", "maritime", "vélo", "velo", "logistique"
            ),
            PolicyTopic.SECURITY to listOf(
                "sécurité", "securite", "police", "gendarmerie", "terrorisme", "criminalité", "criminalite", "délinquance", "delinquance", "ordre public", "renseignement", "protection civile", "cybersécurité", "cybersecurite"
            ),
            PolicyTopic.JUSTICE to listOf(
                "justice", "tribunal", "magistrat", "judiciaire", "pénal", "penal", "prison", "détention", "detention", "procédure civile", "procedure civile", "procédure pénale", "procedure penale", "droit civil"
            ),
            PolicyTopic.IMMIGRATION to listOf(
                "immigration", "asile", "étranger", "etranger", "visa", "nationalité", "nationalite", "séjour", "sejour", "réfugié", "refugie", "intégration", "integration", "expulsion"
            ),
            PolicyTopic.EUROPE to listOf(
                "union européenne", "union europeenne", "europe", "européen", "europeen", "directive européenne", "directive europeenne", "règlement européen", "reglement europeen", "brexit", "zone euro"
            ),
            PolicyTopic.AGRICULTURE to listOf(
                "agriculture", "agricole", "agriculteur", "élevage", "elevage", "pêche", "peche", "alimentation", "souveraineté alimentaire", "souverainete alimentaire", "rural", "viticulture", "forêt", "foret"
            ),
            PolicyTopic.DIGITAL to listOf(
                "numérique", "numerique", "internet", "donnée", "donnee", "données", "donnees", "plateforme", "intelligence artificielle", "cyber", "télécommunications", "telecommunications", "algorithme", "réseau social", "reseau social"
            ),
            PolicyTopic.INSTITUTIONS to listOf(
                "constitution", "institution", "assemblée nationale", "assemblee nationale", "sénat", "senat", "élection", "election", "collectivité territoriale", "collectivite territoriale", "décentralisation", "decentralisation", "fonction publique", "référendum", "referendum"
            ),
            PolicyTopic.FOREIGN_AFFAIRS to listOf(
                "affaires étrangères", "affaires etrangeres", "international", "diplomatie", "traité", "traite", "accord international", "ratification", "défense", "defense", "armée", "armee", "militaire", "coopération", "cooperation"
            )
        )

        private val TOPIC_MATCHERS: List<TopicKeywordMatcher> = TOPIC_KEYWORDS.flatMap { (topic, keywords) ->
            keywords.map { keyword ->
                val normalizedKeyword = keyword.normalizeForTopicMatching()
                TopicKeywordMatcher(
                    topic = topic,
                    keyword = keyword,
                    regex = Regex("(?<!$WORD_BOUNDARY)${Regex.escape(normalizedKeyword)}(?!$WORD_BOUNDARY)")
                )
            }
        }
    }
}

fun List<Vote>.topicVotingSummaries(classifier: PolicyTopicClassifier = PolicyTopicClassifier()): List<TopicVotingSummary> {
    val buckets = linkedMapOf<PolicyTopic, MutableList<Vote>>()
    forEach { vote ->
        val classification = classifier.classify(
            dossierTitle = vote.dossierTitle,
            dossierTitleNormalized = vote.dossierTitle?.normalizeForTopicMatching(),
            legislativeReference = vote.legislativeReference,
            objectTitle = vote.objectTitle
        )
        classification.allTopics.distinct().forEach { topic ->
            buckets.getOrPut(topic) { mutableListOf() }.add(vote)
        }
    }
    return buckets.map { (topic, votes) ->
        TopicVotingSummary(
            topic = topic,
            totalVotes = votes.size,
            pour = votes.count { it.deputePosition == VotePosition.POUR },
            contre = votes.count { it.deputePosition == VotePosition.CONTRE },
            abstention = votes.count { it.deputePosition == VotePosition.ABSTENTION },
            nonVotant = votes.count { it.deputePosition == VotePosition.NON_VOTANT }
        )
    }.sortedWith(compareByDescending<TopicVotingSummary> { it.totalVotes }.thenBy { it.topic.label })
}

private fun String.normalizeForTopicMatching(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .replace('’', '\'')
