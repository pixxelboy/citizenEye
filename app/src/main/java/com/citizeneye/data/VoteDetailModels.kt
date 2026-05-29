package com.citizeneye.data

enum class VoteSubjectType {
    AMENDMENT,
    ARTICLE,
    FULL_TEXT,
    NO_CONFIDENCE_MOTION,
    PROCEDURAL_MOTION,
    GOVERNMENT_STATEMENT,
    BUDGET,
    RESOLUTION,
    CONGRESS,
    INTERNATIONAL_AGREEMENT,
    OTHER
}

data class VoteDetail(
    val voteId: String,
    val voteNumber: String,
    val officialTitle: String,
    val plainLanguageTitle: String?,
    val date: String,
    val subjectType: VoteSubjectType,
    val subjectExplanation: String,
    val voteEffectExplanation: String,
    val result: String,
    val deputyPosition: VotePosition,
    val sourceUrl: String?,
    val parentText: ParentTextDetails?,
    val amendment: AmendmentDetails?,
    val article: ArticleDetails?,
    val motion: MotionDetails?,
    val voteBreakdown: VoteBreakdown?,
    val groupPosition: GroupVotePosition?,
    val officialSources: List<OfficialSource>,
    val externalResources: ExternalResourcesState
)

data class ParentTextDetails(
    val title: String?,
    val type: String?,
    val procedureStage: String?,
    val legislature: String?,
    val dossierUrl: String?,
    val commissionName: String?,
    val rapporteurs: List<String>,
    val depositNumber: String?,
    val adoptionStatus: String?
)

data class AmendmentDetails(
    val number: String?,
    val authors: List<String>,
    val group: String?,
    val status: String?,
    val objectText: String?,
    val proposedChangeText: String?,
    val explanatoryStatement: String?,
    val sourceUrl: String?
)

data class ArticleDetails(
    val number: String?,
    val title: String?,
    val summary: String?,
    val sourceUrl: String?
)

data class MotionDetails(
    val type: String?,
    val authors: List<String>,
    val explanation: String?,
    val politicalEffectExplanation: String?,
    val sourceUrl: String?
)

data class VoteBreakdown(
    val totalVoters: Int?,
    val forCount: Int?,
    val againstCount: Int?,
    val abstentionCount: Int?,
    val nonVotingCount: Int?,
    val absoluteMajority: Int?,
    val resultLabel: String?
)

data class GroupVotePosition(
    val groupName: String,
    val groupMajorityPosition: VotePosition?,
    val deputyVotedLikeGroup: Boolean?,
    val forCount: Int?,
    val againstCount: Int?,
    val abstentionCount: Int?,
    val nonVotingCount: Int?
)

data class OfficialSource(
    val label: String,
    val description: String?,
    val url: String,
    val sourceType: OfficialSourceType
)

enum class OfficialSourceType {
    PUBLIC_VOTE,
    LEGISLATIVE_FILE,
    AMENDMENT,
    DEBATE,
    TEXT,
    REPORT,
    MINUTES,
    OTHER
}

sealed interface ExternalResourcesState {
    data object NotLoaded : ExternalResourcesState
    data object Loading : ExternalResourcesState
    data class Available(
        val newsArticles: List<NewsArticle>,
        val videos: List<VideoResource>,
        val webLinks: List<WebResource>
    ) : ExternalResourcesState
    data class Empty(val message: String) : ExternalResourcesState
    data class Error(val message: String) : ExternalResourcesState
}

data class NewsArticle(
    val title: String,
    val sourceName: String?,
    val author: String?,
    val publishedAt: String?,
    val description: String?,
    val url: String,
    val imageUrl: String?
)

data class VideoResource(
    val title: String,
    val channelName: String?,
    val publishedAt: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val url: String
)

data class WebResource(
    val title: String,
    val sourceName: String?,
    val description: String?,
    val url: String,
    val publishedAt: String?
)

data class ExternalResourceQuery(
    val voteTitle: String,
    val parentTextTitle: String?,
    val date: String?,
    val subjectType: VoteSubjectType,
    val officialKeywords: List<String>
)

fun classifyVoteSubjectType(title: String): VoteSubjectType {
    val text = title.lowercase()
    return when {
        text.contains("motion de censure") -> VoteSubjectType.NO_CONFIDENCE_MOTION
        text.contains("motion") -> VoteSubjectType.PROCEDURAL_MOTION
        text.contains("amendement") || text.contains("amendment") -> VoteSubjectType.AMENDMENT
        text.contains("article") -> VoteSubjectType.ARTICLE
        text.contains("sur l'ensemble") ||
            text.contains("sur l’ensemble") ||
            text.contains("ensemble du projet") ||
            text.contains("ensemble de la proposition") ||
            text.contains("l'ensemble du texte") ||
            text.contains("l’ensemble du texte") -> VoteSubjectType.FULL_TEXT
        text.contains("déclaration du gouvernement") || text.contains("declaration du gouvernement") -> VoteSubjectType.GOVERNMENT_STATEMENT
        text.contains("projet de loi de finances") ||
            text.contains("projet de loi de financement de la sécurité sociale") ||
            text.contains("projet de loi de financement de la securite sociale") ||
            Regex("\\bplf\\b").containsMatchIn(text) ||
            Regex("\\bplfss\\b").containsMatchIn(text) -> VoteSubjectType.BUDGET
        text.contains("résolution") || text.contains("resolution") -> VoteSubjectType.RESOLUTION
        text.contains("congrès") || text.contains("congres") -> VoteSubjectType.CONGRESS
        text.contains("accord") || text.contains("convention") || text.contains("ratification") -> VoteSubjectType.INTERNATIONAL_AGREEMENT
        else -> VoteSubjectType.OTHER
    }
}

fun buildSubjectExplanation(subjectType: VoteSubjectType): String = when (subjectType) {
    VoteSubjectType.AMENDMENT -> "Ce vote porte sur une modification proposée au texte en discussion. Un amendement peut ajouter, supprimer ou modifier une partie précise du texte."
    VoteSubjectType.ARTICLE -> "Ce vote porte sur un article du texte. Un article est une section juridique précise du projet ou de la proposition de loi."
    VoteSubjectType.FULL_TEXT -> "Ce vote porte sur l’adoption ou le rejet de l’ensemble du texte à cette étape de la procédure parlementaire."
    VoteSubjectType.NO_CONFIDENCE_MOTION -> "Ce vote porte sur une motion de censure. Voter pour la motion revient à soutenir la mise en cause de la responsabilité du Gouvernement."
    VoteSubjectType.PROCEDURAL_MOTION -> "Ce vote porte sur une motion de procédure. Ce type de vote peut modifier, interrompre ou réorienter l’examen d’un texte."
    VoteSubjectType.GOVERNMENT_STATEMENT -> "Ce vote porte sur une déclaration du Gouvernement. Il exprime une approbation ou un rejet de cette déclaration."
    VoteSubjectType.BUDGET -> "Ce vote porte sur un texte budgétaire ou une section budgétaire. Il peut concerner les recettes, les dépenses ou l’équilibre financier."
    VoteSubjectType.RESOLUTION -> "Ce vote porte sur une résolution. Une résolution exprime une position de l’Assemblée nationale et n’a pas toujours le même effet juridique qu’une loi."
    VoteSubjectType.CONGRESS -> "Ce vote relève d’une procédure institutionnelle spécifique, souvent constitutionnelle."
    VoteSubjectType.INTERNATIONAL_AGREEMENT -> "Ce vote concerne l’approbation ou la ratification d’un accord, d’une convention ou d’un engagement international."
    VoteSubjectType.OTHER -> "Ce scrutin public concerne un objet parlementaire dont la nature exacte doit être vérifiée dans la source officielle."
}

fun buildVoteEffectExplanation(subjectType: VoteSubjectType, deputyPosition: VotePosition, result: String): String = when (deputyPosition) {
    VotePosition.ABSTENTION -> "La position ABSTENTION signifie que le député n’a voté ni pour ni contre. Résultat officiel : $result."
    VotePosition.NON_VOTANT -> "La position NON-VOTANT signifie qu’aucun vote nominatif pour ou contre n’est enregistré pour ce député sur ce scrutin public. Résultat officiel : $result."
    VotePosition.POUR -> when (subjectType) {
        VoteSubjectType.AMENDMENT -> "La position POUR signifie : voter pour l’adoption de l’amendement."
        VoteSubjectType.FULL_TEXT -> "La position POUR signifie : voter pour l’adoption du texte à cette étape."
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "La position POUR signifie : voter pour l’adoption de la motion de censure."
        VoteSubjectType.PROCEDURAL_MOTION -> "La position POUR signifie : voter pour la motion de procédure, qui peut bloquer, retarder ou réorienter l’examen du texte."
        VoteSubjectType.ARTICLE -> "La position POUR signifie : voter pour l’adoption de cet article du texte."
        else -> "La position POUR signifie : voter pour l’objet soumis à ce scrutin public. Vérifiez le titre officiel pour l’objet exact."
    }
    VotePosition.CONTRE -> when (subjectType) {
        VoteSubjectType.AMENDMENT -> "La position CONTRE signifie : voter contre l’adoption de l’amendement."
        VoteSubjectType.FULL_TEXT -> "La position CONTRE signifie : voter contre l’adoption du texte à cette étape."
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "La position CONTRE signifie : voter contre l’adoption de la motion de censure."
        VoteSubjectType.PROCEDURAL_MOTION -> "La position CONTRE signifie : voter contre la motion de procédure."
        VoteSubjectType.ARTICLE -> "La position CONTRE signifie : voter contre l’adoption de cet article du texte."
        else -> "La position CONTRE signifie : voter contre l’objet soumis à ce scrutin public. Vérifiez le titre officiel pour l’objet exact."
    }
}

fun VoteSubjectType.badgeLabel(): String = when (this) {
    VoteSubjectType.AMENDMENT -> "Amendement"
    VoteSubjectType.ARTICLE -> "Article"
    VoteSubjectType.FULL_TEXT -> "Texte complet"
    VoteSubjectType.NO_CONFIDENCE_MOTION -> "Motion de censure"
    VoteSubjectType.PROCEDURAL_MOTION -> "Motion de procédure"
    VoteSubjectType.GOVERNMENT_STATEMENT -> "Déclaration du Gouvernement"
    VoteSubjectType.BUDGET -> "Budget"
    VoteSubjectType.RESOLUTION -> "Résolution"
    VoteSubjectType.CONGRESS -> "Congrès"
    VoteSubjectType.INTERNATIONAL_AGREEMENT -> "Accord international"
    VoteSubjectType.OTHER -> "Autre scrutin public"
}
