package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingVoteModelTest {
    @Test
    fun upcomingVoteUsesDedicatedLifecycleStatusesWithoutPredictions() {
        val upcoming = UpcomingVote(
            id = "DLR-test",
            title = "Proposition de loi relative à un sujet public",
            shortSummary = "Texte en cours de parcours parlementaire.",
            citizenSummary = "Texte en cours de parcours parlementaire.",
            currentStage = "Examen en commission",
            status = UpcomingVoteStatus.COMMITTEE_REVIEW,
            expectedDateLabel = "Date non annoncée",
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/recherche?search=test"
        )

        assertEquals("Examen en commission", upcoming.status.label)
        assertEquals("Date non annoncée", upcoming.expectedDateLabel)
        assertTrue(upcoming.sourceUrl.startsWith("https://www.assemblee-nationale.fr"))
    }

    @Test
    fun historicalVoteIsSeparateFromUpcomingVote() {
        val vote = Vote(
            id = "VTANR5L17V1",
            number = "1",
            date = "2025-01-01",
            title = "Scrutin public sur un texte",
            result = "Adopté",
            summary = "Pour 10, contre 4, abstentions 1.",
            deputePosition = VotePosition.POUR,
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/1"
        )

        val historical = vote.asHistoricalVote()

        assertEquals(vote.id, historical.id)
        assertEquals(vote.date, historical.date)
        assertEquals(VotePosition.POUR, historical.deputePosition)
    }
}
