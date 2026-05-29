package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeputeMatchPaginationTest {
    private val commune = Commune("Nanterre", "92050", listOf("92000"), "92")
    private val depute = Depute("PA1", "Députée Test", "Groupe Test", "Hauts-de-Seine", "92", "4", null)

    @Test fun exposesOnlyFirstPageWhileKeepingFullLegislatureStats() {
        val match = DeputeMatch(
            query = "92000",
            commune = commune,
            depute = depute,
            allLegislatureVotes = (1..45).map { vote(it) }
        )

        assertEquals(45, match.totalLegislatureVotes)
        assertEquals(20, match.recentVotes.size)
        assertEquals(45, match.legislatureStats.totalVotes)
        assertTrue(match.hasMoreVotes)
    }

    @Test fun loadsAdditionalVotePagesWithoutLosingStatsDenominator() {
        val match = DeputeMatch(
            query = "92000",
            commune = commune,
            depute = depute,
            allLegislatureVotes = (1..45).map { vote(it) }
        )

        val secondPage = match.withMoreVisibleVotes()
        val finalPage = secondPage.withMoreVisibleVotes()

        assertEquals(40, secondPage.recentVotes.size)
        assertTrue(secondPage.hasMoreVotes)
        assertEquals(45, finalPage.recentVotes.size)
        assertFalse(finalPage.hasMoreVotes)
        assertEquals(45, finalPage.legislatureStats.totalVotes)
    }

    private fun vote(number: Int) = Vote(
        id = "V$number",
        number = number.toString(),
        date = "2026-05-${(number % 28 + 1).toString().padStart(2, '0')}",
        title = "Scrutin $number",
        result = "Adopté",
        summary = "Résumé",
        deputePosition = when (number % 4) {
            0 -> VotePosition.POUR
            1 -> VotePosition.CONTRE
            2 -> VotePosition.ABSTENTION
            else -> VotePosition.NON_VOTANT
        },
        sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/$number"
    )
}
