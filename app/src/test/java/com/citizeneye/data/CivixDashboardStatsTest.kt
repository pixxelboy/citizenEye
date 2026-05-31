package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CivixDashboardStatsTest {
    @Test
    fun computesScanFirstAlignmentAndDistributionWithoutDecimals() {
        val votes = listOf(
            vote("1", VotePosition.POUR, VotePosition.POUR),
            vote("2", VotePosition.CONTRE, VotePosition.POUR),
            vote("3", VotePosition.ABSTENTION, VotePosition.ABSTENTION),
            vote("4", VotePosition.NON_VOTANT, null)
        )

        val stats = DeputyStats.from(votes)

        assertEquals(4, stats.totalVotes)
        assertEquals(75, stats.participationPercent)
        assertEquals(67, stats.alignmentRate)
        assertEquals(33, stats.dissidentRate)
        assertEquals(25, stats.percentFor(VotePosition.POUR))
        assertEquals(25, stats.percentFor(VotePosition.CONTRE))
        assertEquals(25, stats.percentFor(VotePosition.ABSTENTION))
        assertEquals(25, stats.percentFor(VotePosition.NON_VOTANT))
    }

    @Test
    fun exposesVoteAlignmentLabelsForCardsAndDetails() {
        assertEquals(VoteGroupAlignment.ALIGNED, vote("1", VotePosition.POUR, VotePosition.POUR).groupAlignment)
        assertEquals(VoteGroupAlignment.DISSIDENT, vote("2", VotePosition.CONTRE, VotePosition.POUR).groupAlignment)
        assertEquals(VoteGroupAlignment.UNKNOWN, vote("3", VotePosition.POUR, null).groupAlignment)
    }

    private fun vote(id: String, deputy: VotePosition, group: VotePosition?): Vote = Vote(
        id = id,
        number = id,
        date = "2026-04-01",
        title = "Scrutin public test",
        result = "Adopté",
        summary = "Résumé",
        deputePosition = deputy,
        sourceUrl = "https://example.test/$id",
        voteBreakdown = VoteBreakdown(totalVoters = 100, forCount = 60, againstCount = 30, abstentionCount = 10, nonVotingCount = 0, absoluteMajority = null, resultLabel = "Adopté"),
        groupPosition = group?.let {
            GroupVotePosition(
                groupName = "Groupe test",
                groupMajorityPosition = it,
                deputyVotedLikeGroup = it == deputy,
                forCount = if (it == VotePosition.POUR) 10 else 2,
                againstCount = if (it == VotePosition.CONTRE) 10 else 2,
                abstentionCount = if (it == VotePosition.ABSTENTION) 10 else 0,
                nonVotingCount = 0
            )
        }
    )
}
