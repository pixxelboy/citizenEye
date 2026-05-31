package com.citizeneye.data

import kotlin.math.abs
import kotlin.math.roundToInt

data class IndependenceSummary(
    val comparableVotes: Int,
    val alignedVotes: Int,
    val dissidentVotes: Int,
    val alignmentPercentage: Int,
    val dissidentPercentage: Int,
    val recentDissidentVotes: List<Vote>
)

data class PolicyTopicDistribution(
    val topic: PolicyTopic,
    val voteCount: Int,
    val percentage: Int
)

enum class ImportanceLevel(val label: String) {
    LOW("Faible"),
    MEDIUM("Moyen"),
    HIGH("Important"),
    CRITICAL("Critique")
}

enum class ImportanceReason {
    HIGH_PARTICIPATION,
    NARROW_MARGIN,
    GOVERNMENT_BACKED_LEGISLATION,
    BUDGET_LEGISLATION,
    CONSTITUTIONAL_LEGISLATION,
    EMERGENCY_PROCEDURE,
    FINAL_OR_DECISIVE_STAGE,
    MANY_REFERENCES
}

data class VoteImportance(
    val level: ImportanceLevel,
    val score: Int,
    val reasons: Set<ImportanceReason>
)

data class LegislativeTimelineStep(
    val label: String,
    val completed: Boolean,
    val current: Boolean
)

fun List<Vote>.independenceSummary(): IndependenceSummary {
    val comparable = filter { it.groupAlignment != VoteGroupAlignment.UNKNOWN }
    val aligned = comparable.count { it.groupAlignment == VoteGroupAlignment.ALIGNED }
    val dissidentVotes = comparable.filter { it.groupAlignment == VoteGroupAlignment.DISSIDENT }
    val dissident = dissidentVotes.size
    return IndependenceSummary(
        comparableVotes = comparable.size,
        alignedVotes = aligned,
        dissidentVotes = dissident,
        alignmentPercentage = percent(aligned, comparable.size),
        dissidentPercentage = percent(dissident, comparable.size),
        recentDissidentVotes = dissidentVotes.sortedByDescending { it.date }.take(5)
    )
}

fun List<Vote>.policyTopicDistribution(classifier: PolicyTopicClassifier = PolicyTopicClassifier()): List<PolicyTopicDistribution> {
    if (isEmpty()) return emptyList()
    val counts = linkedMapOf<PolicyTopic, Int>()
    forEach { vote ->
        val classification = classifier.classify(
            dossierTitle = vote.dossierTitle,
            legislativeReference = vote.legislativeReference,
            objectTitle = vote.objectTitle
        )
        classification.allTopics.distinct().forEach { topic -> counts[topic] = (counts[topic] ?: 0) + 1 }
    }
    val totalAssignments = counts.values.sum().coerceAtLeast(1)
    return counts.map { (topic, count) ->
        PolicyTopicDistribution(topic = topic, voteCount = count, percentage = percent(count, totalAssignments))
    }.sortedWith(compareByDescending<PolicyTopicDistribution> { it.voteCount }.thenBy { it.topic.label })
}

val Vote.importance: VoteImportance
    get() {
        val reasons = linkedSetOf<ImportanceReason>()
        var score = 0
        val breakdown = voteBreakdown
        val totalVoters = breakdown?.totalVoters ?: listOfNotNull(breakdown?.forCount, breakdown?.againstCount, breakdown?.abstentionCount).sum().takeIf { it > 0 } ?: 0
        if (totalVoters >= 500) {
            score += 30
            reasons += ImportanceReason.HIGH_PARTICIPATION
        } else if (totalVoters >= 250) {
            score += 15
            reasons += ImportanceReason.HIGH_PARTICIPATION
        }
        val pour = breakdown?.forCount
        val contre = breakdown?.againstCount
        if (pour != null && contre != null) {
            val expressed = pour + contre + (breakdown.abstentionCount ?: 0)
            val margin = abs(pour - contre)
            if (expressed > 0 && margin.toDouble() / expressed.toDouble() <= 0.05) {
                score += 30
                reasons += ImportanceReason.NARROW_MARGIN
            } else if (expressed > 0 && margin.toDouble() / expressed.toDouble() <= 0.12) {
                score += 15
                reasons += ImportanceReason.NARROW_MARGIN
            }
        }
        val text = listOf(title, summary, dossierTitle, objectTitle, legislativeReference).filterNotNull().joinToString(" ").lowercase()
        if (text.contains("gouvernement") || text.contains("projet de loi")) {
            score += 10
            reasons += ImportanceReason.GOVERNMENT_BACKED_LEGISLATION
        }
        if (text.contains("loi de finances") || text.contains("budget") || text.contains("financement de la sécurité sociale") || text.contains("financement de la securite sociale")) {
            score += 20
            reasons += ImportanceReason.BUDGET_LEGISLATION
        }
        if (text.contains("constitution") || text.contains("constitutionnel") || text.contains("organique")) {
            score += 20
            reasons += ImportanceReason.CONSTITUTIONAL_LEGISLATION
        }
        if (text.contains("urgence") || text.contains("procédure accélérée") || text.contains("procedure acceleree")) {
            score += 15
            reasons += ImportanceReason.EMERGENCY_PROCEDURE
        }
        if (text.contains("lecture définitive") || text.contains("lecture definitive") || text.contains("adoption définitive") || text.contains("adoption definitive")) {
            score += 15
            reasons += ImportanceReason.FINAL_OR_DECISIVE_STAGE
        }
        val referenceCount = listOfNotNull(dossierRef, dossierTitle, legislativeReference, seanceRef, sourceUrl.takeIf { it.isNotBlank() }).size
        if (referenceCount >= 4) {
            score += 5
            reasons += ImportanceReason.MANY_REFERENCES
        }
        val level = when {
            score >= 70 -> ImportanceLevel.CRITICAL
            score >= 45 -> ImportanceLevel.HIGH
            score >= 20 -> ImportanceLevel.MEDIUM
            else -> ImportanceLevel.LOW
        }
        return VoteImportance(level = level, score = score, reasons = reasons)
    }

val UpcomingVote.legislativeTimeline: List<LegislativeTimelineStep>
    get() {
        val supported = timeline.mapNotNull { event -> normalizeTimelineLabel(event.label) }.distinct()
        val current = normalizeTimelineLabel(currentStage) ?: supported.lastOrNull()
        if (supported.isEmpty() && current == null) return emptyList()
        val labels = if (current != null && current !in supported) supported + current else supported
        val currentIndex = labels.indexOf(current).coerceAtLeast(labels.lastIndex)
        return labels.mapIndexed { index, label ->
            LegislativeTimelineStep(
                label = label,
                completed = index <= currentIndex,
                current = label == current
            )
        }
    }

val UpcomingVote.isContactMomentRelevant: Boolean
    get() = status == UpcomingVoteStatus.SCHEDULED_VOTE ||
        status == UpcomingVoteStatus.UNDER_DISCUSSION ||
        status == UpcomingVoteStatus.COMMITTEE_REVIEW ||
        currentStage.isNotBlank()

fun List<UpcomingVote>.hasRelevantContactMoment(): Boolean = any { it.isContactMomentRelevant }

private fun normalizeTimelineLabel(value: String): String? {
    val text = value.lowercase()
    return when {
        text.contains("dépôt") || text.contains("depot") || text.contains("déposé") || text.contains("depose") -> "Dépôt"
        text.contains("commission mixte") -> "Commission mixte"
        text.contains("commission") -> "Commission"
        text.contains("assemblée") || text.contains("assemblee") || text.contains("séance") || text.contains("seance") -> "Assemblée"
        text.contains("sénat") || text.contains("senat") -> "Sénat"
        text.contains("constitutionnel") -> "Contrôle constitutionnel"
        text.contains("promulgation") -> "Promulgation"
        text.contains("publication") -> "Publication"
        else -> null
    }
}

private fun percent(value: Int, total: Int): Int = if (total <= 0) 0 else ((value.toDouble() / total.toDouble()) * 100).roundToInt()
