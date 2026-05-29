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
    VoteSubjectType.AMENDMENT -> "This vote was about a proposed change to a text under discussion. An amendment can add, remove, or modify a specific part of the text."
    VoteSubjectType.ARTICLE -> "This vote was about an article of the text. An article is a specific legal section of the bill or proposal."
    VoteSubjectType.FULL_TEXT -> "This vote was about adopting or rejecting the whole text at this stage of the legislative process."
    VoteSubjectType.NO_CONFIDENCE_MOTION -> "This vote was about a no-confidence motion. Voting for the motion means supporting a challenge to the Government’s responsibility."
    VoteSubjectType.PROCEDURAL_MOTION -> "This vote was about a procedural motion. This kind of vote can change, interrupt, or redirect the discussion of a text."
    VoteSubjectType.GOVERNMENT_STATEMENT -> "This vote was about a Government statement. It expresses approval or rejection of that statement."
    VoteSubjectType.BUDGET -> "This vote was about a budget text or a budget section. These votes may concern revenue, spending, or financial balance."
    VoteSubjectType.RESOLUTION -> "This vote was about a resolution. A resolution expresses a position of the Assemblée nationale and does not always have the same legal effect as a law."
    VoteSubjectType.CONGRESS -> "This vote belongs to a specific institutional process, often constitutional."
    VoteSubjectType.INTERNATIONAL_AGREEMENT -> "This vote concerned approval or ratification of an agreement, convention, or international commitment."
    VoteSubjectType.OTHER -> "This public vote concerns a parliamentary item whose exact nature should be checked in the official source."
}

fun buildVoteEffectExplanation(subjectType: VoteSubjectType, deputyPosition: VotePosition, result: String): String = when (deputyPosition) {
    VotePosition.ABSTENTION -> "The ABSTENTION position means the MP voted neither for nor against. Official result: $result."
    VotePosition.NON_VOTANT -> "The NON-VOTING position means no nominal for/against vote is recorded for this MP on this public vote. Official result: $result."
    VotePosition.POUR -> when (subjectType) {
        VoteSubjectType.AMENDMENT -> "The FOR position means: voting for the adoption of the amendment."
        VoteSubjectType.FULL_TEXT -> "The FOR position means: voting for adopting the text at this stage."
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "The FOR position means: voting for adopting the no-confidence motion."
        VoteSubjectType.PROCEDURAL_MOTION -> "The FOR position means: voting for the procedural motion, which may block, delay, or redirect the examination of the text."
        VoteSubjectType.ARTICLE -> "The FOR position means: voting for adopting this article of the text."
        else -> "The FOR position means: voting for the item submitted to this public vote. Check the official title for the exact object."
    }
    VotePosition.CONTRE -> when (subjectType) {
        VoteSubjectType.AMENDMENT -> "The AGAINST position means: voting against the adoption of the amendment."
        VoteSubjectType.FULL_TEXT -> "The AGAINST position means: voting against adopting the text at this stage."
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "The AGAINST position means: voting against adopting the no-confidence motion."
        VoteSubjectType.PROCEDURAL_MOTION -> "The AGAINST position means: voting against the procedural motion."
        VoteSubjectType.ARTICLE -> "The AGAINST position means: voting against adopting this article of the text."
        else -> "The AGAINST position means: voting against the item submitted to this public vote. Check the official title for the exact object."
    }
}

fun VoteSubjectType.badgeLabel(): String = when (this) {
    VoteSubjectType.AMENDMENT -> "Amendment"
    VoteSubjectType.ARTICLE -> "Article"
    VoteSubjectType.FULL_TEXT -> "Full text"
    VoteSubjectType.NO_CONFIDENCE_MOTION -> "No-confidence motion"
    VoteSubjectType.PROCEDURAL_MOTION -> "Procedural motion"
    VoteSubjectType.GOVERNMENT_STATEMENT -> "Government statement"
    VoteSubjectType.BUDGET -> "Budget"
    VoteSubjectType.RESOLUTION -> "Resolution"
    VoteSubjectType.CONGRESS -> "Congress"
    VoteSubjectType.INTERNATIONAL_AGREEMENT -> "International agreement"
    VoteSubjectType.OTHER -> "Other public vote"
}
