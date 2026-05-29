package com.citizeneye.data

fun isTechnicalId(value: String): Boolean {
    val clean = value.trim()
    if (clean.isEmpty()) return false
    if (Regex("^(PO|PA)\\d+$").matches(clean)) return true
    if (Regex("^[A-Z]{2,}[-_A-Z0-9]*\\d*[A-Z0-9]*$").matches(clean) && !clean.contains(' ')) return true
    return false
}

fun safeDisplayValue(value: String?): String? {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return null
    if (isTechnicalId(clean)) return null
    return clean
}

fun formatPositionInContext(position: VotePosition, subjectType: VoteSubjectType): String = when (position) {
    VotePosition.ABSTENTION -> "Abstention"
    VotePosition.NON_VOTANT -> "Non-votant"
    VotePosition.POUR -> when (subjectType) {
        VoteSubjectType.AMENDMENT -> "Pour l’amendement"
        VoteSubjectType.ARTICLE -> "Pour l’article"
        VoteSubjectType.FULL_TEXT -> "Pour le texte"
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "Pour la motion de censure"
        VoteSubjectType.PROCEDURAL_MOTION -> "Pour la motion"
        else -> "Pour"
    }
    VotePosition.CONTRE -> when (subjectType) {
        VoteSubjectType.AMENDMENT -> "Contre l’amendement"
        VoteSubjectType.ARTICLE -> "Contre l’article"
        VoteSubjectType.FULL_TEXT -> "Contre le texte"
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "Contre la motion de censure"
        VoteSubjectType.PROCEDURAL_MOTION -> "Contre la motion"
        else -> "Contre"
    }
}

fun formatVoteResultLabel(subjectType: VoteSubjectType, result: String): String {
    val lower = result.lowercase()
    val adopted = lower.contains("adopt") || lower.contains("pour") && !lower.contains("rejet")
    val rejected = lower.contains("rejet") || lower.contains("contre") && !lower.contains("adopt")
    val subject = when (subjectType) {
        VoteSubjectType.AMENDMENT -> "Amendement"
        VoteSubjectType.ARTICLE -> "Article"
        VoteSubjectType.FULL_TEXT -> "Texte"
        VoteSubjectType.NO_CONFIDENCE_MOTION -> "Motion de censure"
        VoteSubjectType.PROCEDURAL_MOTION -> "Motion"
        VoteSubjectType.RESOLUTION -> "Résolution"
        else -> "Scrutin"
    }
    return when {
        adopted -> "$subject adopté"
        rejected -> "$subject rejeté"
        else -> result.ifBlank { "Résultat non disponible" }
    }
}

fun buildPositionResultSentence(position: VotePosition, subjectType: VoteSubjectType, result: String): String {
    val contextualPosition = formatPositionInContext(position, subjectType)
    val plainResult = formatVoteResultLabel(subjectType, result).replaceFirstChar { it.lowercase() }
    return "Position enregistrée : $contextualPosition, résultat : $plainResult."
}

fun buildTakeaway(subjectType: VoteSubjectType): String = when (subjectType) {
    VoteSubjectType.AMENDMENT -> "Ce scrutin ne portait pas sur toute la loi, mais sur une modification précise du texte."
    VoteSubjectType.FULL_TEXT -> "Ce scrutin portait sur l’adoption ou le rejet du texte à cette étape de la procédure."
    VoteSubjectType.NO_CONFIDENCE_MOTION -> "Attention : voter « Pour » une motion de censure signifie soutenir la mise en cause de la responsabilité du Gouvernement."
    VoteSubjectType.PROCEDURAL_MOTION -> "Attention : voter « Pour » une motion signifie soutenir la motion. Selon le type de motion, cela peut bloquer, rejeter ou réorienter l’examen du texte."
    else -> "Le sens de la position dépend de l’objet exact soumis au vote. Vérifiez le titre officiel du scrutin."
}

fun buildEmailSubject(voteNumber: String): String = "Question sur votre vote au scrutin public n°$voteNumber"

fun buildEmailBody(
    voteNumber: String,
    voteDate: String,
    officialVoteTitle: String,
    deputyPosition: String,
    sourceUrl: String?,
    includeDisagreement: Boolean = false
): String = buildString {
    appendLine("Madame, Monsieur,")
    appendLine()
    appendLine("Je vous écris au sujet du scrutin public n°$voteNumber du $voteDate, concernant :")
    appendLine()
    appendLine(officialVoteTitle)
    appendLine()
    appendLine("Selon la source officielle de l’Assemblée nationale, votre position enregistrée était : $deputyPosition.")
    appendLine()
    if (includeDisagreement) {
        appendLine("Je ne me sens pas aligné avec cette position et souhaiterais comprendre votre raisonnement.")
        appendLine()
    }
    appendLine("Je souhaite mieux comprendre les raisons de cette position et la manière dont vous l’inscrivez dans votre mandat de représentation de la circonscription.")
    appendLine()
    appendLine("Source officielle :")
    appendLine(sourceUrl ?: "Non disponible pour le moment")
    appendLine()
    appendLine("Cordialement,")
}
