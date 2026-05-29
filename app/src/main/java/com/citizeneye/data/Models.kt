package com.citizeneye.data

data class Commune(
    val name: String,
    val code: String,
    val postalCodes: List<String>,
    val departmentCode: String?
)

data class Depute(
    val id: String,
    val name: String,
    /**
     * Compact political group label kept for compatibility with existing code.
     * Prefer displayPoliticalGroupShort in UI code.
     */
    val group: String,
    val departmentName: String,
    val departmentCode: String,
    val constituencyNumber: String,
    val email: String?,
    val regionName: String? = null,
    val profession: String? = null,
    val politicalGroupFullName: String = group.takeIf { it.isNotBlank() } ?: "Groupe non renseigné",
    val politicalGroupAbbreviation: String = group.takeIf { it.isNotBlank() } ?: "N/R",
    val photoUrl: String? = deputyPhotoUrl(id)
) {
    val constituencyLabel: String get() = "$departmentName — ${constituencyNumber}e circonscription"
    val displayPoliticalGroupShort: String get() = politicalGroupAbbreviation.takeIf { it.isNotBlank() } ?: group.takeIf { it.isNotBlank() } ?: "N/R"
    val displayPoliticalGroupFull: String get() = politicalGroupFullName.takeIf { it.isNotBlank() } ?: "Groupe non renseigné"
    val displayProfession: String get() = profession?.takeIf { it.isNotBlank() } ?: "Non renseignée"
    val displayRegion: String get() = regionName?.takeIf { it.isNotBlank() } ?: "Non renseignée"
    val displayDepartment: String get() = when {
        departmentName.isNotBlank() && departmentCode.isNotBlank() -> "$departmentName ($departmentCode)"
        departmentName.isNotBlank() -> departmentName
        departmentCode.isNotBlank() -> departmentCode
        else -> "Non renseigné"
    }
}

data class Vote(
    val id: String,
    val number: String,
    val date: String,
    val title: String,
    val result: String,
    val summary: String,
    val deputePosition: VotePosition,
    val sourceUrl: String,
    val voteBreakdown: VoteBreakdown? = null,
    val groupPosition: GroupVotePosition? = null,
    val objectTitle: String? = null,
    val dossierRef: String? = null,
    val dossierTitle: String? = null,
    val legislativeReference: String? = null,
    val seanceRef: String? = null
) {
    val concern: VoteConcern get() = classifyVoteConcern(title, summary)
}

enum class VoteConcern(val label: String, val explanation: String) {
    AMENDEMENT("Amendement", "Modifie une partie précise du texte"),
    ARTICLE("Article", "Vote sur une section du texte"),
    TEXTE_COMPLET("Texte complet", "Vote d’adoption/rejet à une étape parlementaire"),
    MOTION("Motion", "Vote de procédure ou de censure"),
    BUDGET("Budget", "Vote budgétaire, souvent par partie"),
    RESOLUTION("Résolution", "Position politique de l’Assemblée")
}

fun classifyVoteConcern(title: String, summary: String = ""): VoteConcern {
    val text = "$title $summary".lowercase()
    return when {
        text.contains("loi de finances") ||
            text.contains("projet de loi de finances") ||
            text.contains("budget") ||
            text.contains("financement de la sécurité sociale") ||
            text.contains("financement de la securite sociale") -> VoteConcern.BUDGET
        text.contains("motion") ||
            text.contains("censure") ||
            text.contains("rejet préalable") ||
            text.contains("rejet prealable") ||
            text.contains("question préalable") ||
            text.contains("question prealable") ||
            text.contains("renvoi en commission") -> VoteConcern.MOTION
        text.contains("résolution") || text.contains("resolution") -> VoteConcern.RESOLUTION
        text.contains("amendement") -> VoteConcern.AMENDEMENT
        text.contains("article") -> VoteConcern.ARTICLE
        else -> VoteConcern.TEXTE_COMPLET
    }
}

data class LocationPreview(
    val query: String,
    val commune: Commune,
    val deputies: List<Depute>,
    val preciseBoundary: ConstituencyBoundary? = null
) {
    val requiresDeputyChoice: Boolean get() = deputies.size != 1
    val locationLabel: String get() = "${commune.name} · département ${commune.departmentCode ?: "inconnu"}"
    val representativeHint: String get() = if (deputies.size == 1) {
        val depute = deputies.first()
        val precision = if (preciseBoundary != null) " détectée" else ""
        "${depute.name} · ${depute.constituencyNumber}e circonscription$precision"
    } else {
        "${deputies.size} circonscriptions possibles"
    }
}

fun List<Depute>.matching(boundary: ConstituencyBoundary): List<Depute> = filter { depute ->
    depute.departmentCode == boundary.departmentCode && depute.constituencyNumber == boundary.constituencyNumber
}

data class DeputeMatch(
    val query: String,
    val commune: Commune,
    val depute: Depute,
    val allLegislatureVotes: List<Vote>,
    val visibleVoteCount: Int = DEFAULT_VISIBLE_VOTE_COUNT
) {
    val recentVotes: List<Vote> get() = allLegislatureVotes.take(visibleVoteCount)
    val totalLegislatureVotes: Int get() = allLegislatureVotes.size
    val hasMoreVotes: Boolean get() = visibleVoteCount < allLegislatureVotes.size
    val legislatureStats: DeputyStats get() = DeputyStats.from(allLegislatureVotes)

    fun withMoreVisibleVotes(pageSize: Int = DEFAULT_VISIBLE_VOTE_COUNT): DeputeMatch = copy(
        visibleVoteCount = (visibleVoteCount + pageSize).coerceAtMost(allLegislatureVotes.size)
    )

    companion object {
        const val DEFAULT_VISIBLE_VOTE_COUNT = 20
    }
}

enum class VotePosition(val label: String) {
    POUR("Pour"),
    CONTRE("Contre"),
    ABSTENTION("Abstention"),
    NON_VOTANT("Non-votant")
}

sealed interface LookupState {
    data object Idle : LookupState
    data class Loading(val message: String) : LookupState
    data class NeedSelection(
        val query: String,
        val commune: Commune,
        val deputies: List<Depute>,
        val reason: String
    ) : LookupState
    data class Loaded(val match: DeputeMatch) : LookupState
    data class Error(val message: String) : LookupState
}

object CitizenInputValidator {
    fun isZipCode(value: String): Boolean = value.length == 5 && value.all { it.isDigit() }
    fun canSearch(value: String): Boolean = isZipCode(value.trim()) || value.trim().length >= 2
}

internal fun deputyPhotoUrl(actorId: String): String? {
    val numericId = actorId.removePrefix("PA")
    if (numericId.isBlank() || numericId.any { !it.isDigit() }) return null
    return "https://www.assemblee-nationale.fr/dyn/static/tribun/17/photos/carre/$numericId.jpg"
}
