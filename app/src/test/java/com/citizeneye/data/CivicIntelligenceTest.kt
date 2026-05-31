package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CivicIntelligenceTest {
    @Test
    fun independenceSummaryCountsRecentDissidentVotesMostRecentFirst() {
        val votes = listOf(
            vote("old", "2026-01-01", VotePosition.CONTRE, VotePosition.POUR),
            vote("aligned", "2026-03-01", VotePosition.POUR, VotePosition.POUR),
            vote("new", "2026-05-01", VotePosition.ABSTENTION, VotePosition.CONTRE)
        )

        val independence = votes.independenceSummary()

        assertEquals(3, independence.comparableVotes)
        assertEquals(1, independence.alignedVotes)
        assertEquals(2, independence.dissidentVotes)
        assertEquals(33, independence.alignmentPercentage)
        assertEquals(67, independence.dissidentPercentage)
        assertEquals(listOf("new", "old"), independence.recentDissidentVotes.map { it.id })
    }

    @Test
    fun topicDistributionUsesOfficialDossierMetadataAndPercentages() {
        val votes = listOf(
            vote("energy-1", dossierTitle = "Projet de loi relatif à la transition énergétique"),
            vote("energy-2", dossierTitle = "Projet de loi relatif à la transition énergétique"),
            vote("health", dossierTitle = "Proposition de loi sur la santé à l’hôpital")
        )

        val distribution = votes.policyTopicDistribution()

        assertEquals(PolicyTopic.ENERGY, distribution[0].topic)
        assertEquals(67, distribution[0].percentage)
        assertEquals(PolicyTopic.HEALTH, distribution[1].topic)
        assertEquals(33, distribution[1].percentage)
    }

    @Test
    fun importanceLevelIsDeterministicFromOfficialVoteSignals() {
        val critical = vote(
            "budget",
            title = "Projet de loi de finances constitutionnel",
            breakdown = VoteBreakdown(totalVoters = 570, forCount = 286, againstCount = 284, abstentionCount = 0, nonVotingCount = 7, absoluteMajority = null, resultLabel = "Adopté")
        )
        val low = vote(
            "ordinary",
            breakdown = VoteBreakdown(totalVoters = 75, forCount = 70, againstCount = 5, abstentionCount = 0, nonVotingCount = 502, absoluteMajority = null, resultLabel = "Adopté")
        )

        assertEquals(ImportanceLevel.CRITICAL, critical.importance.level)
        assertEquals(ImportanceLevel.LOW, low.importance.level)
        assertTrue(critical.importance.reasons.contains(ImportanceReason.BUDGET_LEGISLATION))
        assertTrue(critical.importance.reasons.contains(ImportanceReason.NARROW_MARGIN))
    }

    @Test
    fun legislativeTimelineKeepsOnlyOfficiallySupportedStagesAndHighlightsCurrent() {
        val upcoming = UpcomingVote(
            id = "DL1",
            title = "Projet de loi énergie",
            shortSummary = "",
            citizenSummary = "",
            currentStage = "Examen en commission",
            status = UpcomingVoteStatus.COMMITTEE_REVIEW,
            expectedDateLabel = "Date non annoncée",
            sourceUrl = "https://assemblee.test",
            timeline = listOf(UpcomingVoteTimelineEvent("Dépôt du texte"), UpcomingVoteTimelineEvent("Examen en commission"))
        )

        val timeline = upcoming.legislativeTimeline

        assertEquals(listOf("Dépôt", "Commission"), timeline.map { it.label })
        assertTrue(timeline[0].completed)
        assertTrue(timeline[1].current)
    }

    @Test
    fun contactMomentIsRelevantOnlyForActiveUpcomingParliamentaryWork() {
        val active = UpcomingVote("a", "Texte", "", "", "Séance publique", UpcomingVoteStatus.UNDER_DISCUSSION, "Dans 4 jours", "https://assemblee.test")
        val empty = emptyList<UpcomingVote>()

        assertTrue(active.isContactMomentRelevant)
        assertTrue(listOf(active).hasRelevantContactMoment())
        assertFalse(empty.hasRelevantContactMoment())
    }

    private fun vote(
        id: String,
        date: String = "2026-04-01",
        deputy: VotePosition = VotePosition.POUR,
        group: VotePosition? = VotePosition.POUR,
        title: String = "Scrutin public",
        dossierTitle: String? = "Dossier officiel",
        breakdown: VoteBreakdown? = VoteBreakdown(totalVoters = 100, forCount = 60, againstCount = 35, abstentionCount = 5, nonVotingCount = 0, absoluteMajority = null, resultLabel = "Adopté")
    ): Vote = Vote(
        id = id,
        number = id,
        date = date,
        title = title,
        result = "Adopté",
        summary = "Résumé officiel",
        deputePosition = deputy,
        sourceUrl = "https://assemblee.test/$id",
        voteBreakdown = breakdown,
        groupPosition = group?.let {
            GroupVotePosition("Groupe", it, it == deputy, forCount = null, againstCount = null, abstentionCount = null, nonVotingCount = null)
        },
        dossierTitle = dossierTitle,
        legislativeReference = null,
        objectTitle = dossierTitle
    )
}
