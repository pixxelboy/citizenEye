package com.citizeneye.data

sealed interface CivicEmailContext {
    val depute: Depute

    data class PastVote(val vote: Vote, override val depute: Depute) : CivicEmailContext
    data class UpcomingVote(val vote: com.citizeneye.data.UpcomingVote, override val depute: Depute) : CivicEmailContext
}

enum class CivicEmailIntent(
    val label: String,
    val description: String,
    private val contextType: CivicEmailContextType
) {
    ASK_EXPLANATION(
        label = "Demander une explication",
        description = "Comprendre les raisons de la position enregistrée.",
        contextType = CivicEmailContextType.PAST_VOTE
    ),
    EXPRESS_DISAGREEMENT(
        label = "Exprimer un désaccord",
        description = "Faire part d’un désaccord de façon respectueuse et sourcée.",
        contextType = CivicEmailContextType.PAST_VOTE
    ),
    EXPRESS_SUPPORT(
        label = "Exprimer un soutien",
        description = "Remercier ou soutenir cette position, avec un message personnel.",
        contextType = CivicEmailContextType.PAST_VOTE
    ),
    ASK_FUTURE_POSITION(
        label = "Demander la suite",
        description = "Demander comment ce sujet sera suivi lors des prochaines étapes.",
        contextType = CivicEmailContextType.PAST_VOTE
    ),
    ARGUE_FOR(
        label = "Partager un argument pour",
        description = "Présenter un soutien ou un argument favorable avant la prochaine étape.",
        contextType = CivicEmailContextType.UPCOMING_VOTE
    ),
    ARGUE_AGAINST(
        label = "Partager une réserve",
        description = "Présenter une opposition ou des réserves de façon calme et factuelle.",
        contextType = CivicEmailContextType.UPCOMING_VOTE
    ),
    ASK_CLARIFICATION(
        label = "Demander une clarification",
        description = "Demander la position actuelle ou les points jugés prioritaires.",
        contextType = CivicEmailContextType.UPCOMING_VOTE
    );

    fun supports(context: CivicEmailContext): Boolean = when (context) {
        is CivicEmailContext.PastVote -> contextType == CivicEmailContextType.PAST_VOTE
        is CivicEmailContext.UpcomingVote -> contextType == CivicEmailContextType.UPCOMING_VOTE
    }

    companion object {
        fun forContext(context: CivicEmailContext): List<CivicEmailIntent> = entries.filter { it.supports(context) }
        fun defaultFor(context: CivicEmailContext): CivicEmailIntent = forContext(context).first()
    }
}

private enum class CivicEmailContextType { PAST_VOTE, UPCOMING_VOTE }

data class CivicEmailDraft(
    val subject: String,
    val body: String
)

const val CIVIC_EMAIL_PERSONALIZATION_PLACEHOLDER: String =
    "Je vous écris en tant que citoyen/ne concerné/e par ce sujet."

fun buildCivicEmailDraft(context: CivicEmailContext, intent: CivicEmailIntent): CivicEmailDraft {
    require(intent.supports(context)) { "L’intention choisie ne correspond pas au contexte civique." }
    return when (context) {
        is CivicEmailContext.PastVote -> buildPastVoteDraft(context.vote, intent)
        is CivicEmailContext.UpcomingVote -> buildUpcomingVoteDraft(context.vote, intent)
    }
}

private fun buildPastVoteDraft(vote: Vote, intent: CivicEmailIntent): CivicEmailDraft {
    val title = vote.title.safeEmailValue()
    val date = vote.date.safeEmailValue()
    val result = vote.result.safeEmailValue()
    val source = vote.sourceUrl.safeEmailValue()
    val position = vote.deputePosition.label.safeEmailValue()
    val intentParagraph = when (intent) {
        CivicEmailIntent.ASK_EXPLANATION -> "Je souhaiterais comprendre les raisons de cette position et les arguments qui l’ont motivée."
        CivicEmailIntent.EXPRESS_DISAGREEMENT -> "Je souhaite vous faire part de mon désaccord avec cette position, dans un esprit de dialogue et à partir des informations publiques disponibles."
        CivicEmailIntent.EXPRESS_SUPPORT -> "Je souhaite vous remercier et vous faire part de mon soutien concernant cette position."
        CivicEmailIntent.ASK_FUTURE_POSITION -> "Pouvez-vous indiquer comment vous envisagez de suivre ce sujet lors des prochaines étapes parlementaires ?"
        else -> "Je souhaite vous écrire au sujet de ce vote."
    }
    val subjectPrefix = when (intent) {
        CivicEmailIntent.ASK_EXPLANATION -> "Question sur votre vote"
        CivicEmailIntent.EXPRESS_DISAGREEMENT -> "Réaction à votre vote"
        CivicEmailIntent.EXPRESS_SUPPORT -> "Soutien à votre vote"
        CivicEmailIntent.ASK_FUTURE_POSITION -> "Suite parlementaire après votre vote"
        else -> "Question sur votre vote"
    }
    val subject = if (vote.date.isBlank()) subjectPrefix else "$subjectPrefix du ${vote.date}"
    return CivicEmailDraft(
        subject = subject.trim().ifBlank { "Question sur votre vote" },
        body = """
Bonjour,

Je vous contacte au sujet du vote suivant : $title.

Date du vote : $date.
D’après les données publiques disponibles, votre position a été : $position.
Résultat du vote : $result.
Source officielle : $source

$intentParagraph

$CIVIC_EMAIL_PERSONALIZATION_PLACEHOLDER

Cordialement,
        """.trimIndent()
    )
}

private fun buildUpcomingVoteDraft(vote: UpcomingVote, intent: CivicEmailIntent): CivicEmailDraft {
    val title = vote.title.safeEmailValue()
    val stage = vote.currentStage.safeEmailValue()
    val status = vote.status.label.safeEmailValue()
    val expectedDate = vote.expectedDateLabel.safeEmailValue()
    val source = vote.sourceUrl.safeEmailValue()
    val intentParagraph = when (intent) {
        CivicEmailIntent.ARGUE_FOR -> "Je souhaite vous faire part de mon soutien à ce texte ou à cette orientation, et vous demander de prendre en compte les arguments suivants avant les prochaines étapes parlementaires."
        CivicEmailIntent.ARGUE_AGAINST -> "Je souhaite vous faire part de mes réserves ou de mon opposition à ce texte, et vous demander de prendre en compte les arguments suivants avant les prochaines étapes parlementaires."
        CivicEmailIntent.ASK_CLARIFICATION -> "Pouvez-vous préciser votre position actuelle ou les points que vous considérez prioritaires sur ce texte ?"
        else -> "Je souhaite vous écrire au sujet de ce texte à venir."
    }
    val subjectTitle = vote.title.compactEmailTitle().ifBlank { "texte parlementaire" }
    return CivicEmailDraft(
        subject = "À propos du texte à venir : $subjectTitle".trim().ifBlank { "À propos d’un texte parlementaire à venir" },
        body = """
Bonjour,

Je vous contacte au sujet du texte parlementaire suivant : $title.

État actuel : $stage.
Statut : $status.
Calendrier : $expectedDate.
Source officielle : $source

$intentParagraph

$CIVIC_EMAIL_PERSONALIZATION_PLACEHOLDER

Cordialement,
        """.trimIndent()
    )
}

private fun String.safeEmailValue(): String = trim().ifBlank { "Non renseigné" }

private fun String.compactEmailTitle(maxChars: Int = 84): String =
    replace("\n", " ").replace(Regex("\\s+"), " ").trim().let { value ->
        if (value.length <= maxChars) value else value.take(maxChars).trimEnd() + "…"
    }
