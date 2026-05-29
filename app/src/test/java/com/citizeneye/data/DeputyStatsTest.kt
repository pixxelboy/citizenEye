package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeputyStatsTest {
    @Test fun computesVoteDistributionAndParticipationFromLoadedVotes() {
        val stats = DeputyStats.from(
            listOf(
                vote("1", VotePosition.POUR),
                vote("2", VotePosition.POUR),
                vote("3", VotePosition.CONTRE),
                vote("4", VotePosition.ABSTENTION),
                vote("5", VotePosition.NON_VOTANT)
            )
        )

        assertEquals(5, stats.totalVotes)
        assertEquals(4, stats.participatedVotes)
        assertEquals(80, stats.participationPercent)
        assertEquals(40, stats.percentFor(VotePosition.POUR))
        assertEquals(20, stats.percentFor(VotePosition.CONTRE))
        assertEquals(20, stats.percentFor(VotePosition.ABSTENTION))
        assertEquals(20, stats.percentFor(VotePosition.NON_VOTANT))
    }

    @Test fun returnsZeroStatsWhenNoVotesAreLoaded() {
        val stats = DeputyStats.from(emptyList())

        assertEquals(0, stats.totalVotes)
        assertEquals(0, stats.participatedVotes)
        assertEquals(0, stats.participationPercent)
        assertEquals(0, stats.percentFor(VotePosition.POUR))
    }

    private fun vote(id: String, position: VotePosition) = Vote(
        id = id,
        number = id,
        date = "2026-05-${id.padStart(2, '0')}",
        title = "Scrutin $id",
        result = "Adopté",
        summary = "Résumé",
        deputePosition = position,
        sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/$id"
    )
}
