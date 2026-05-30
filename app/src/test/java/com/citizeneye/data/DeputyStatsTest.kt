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
        assertEquals(0, stats.groupAlignmentPercent)
    }

    @Test fun computesGroupAlignmentAndComparisonAveragesFromOfficialBreakdowns() {
        val stats = DeputyStats.from(
            listOf(
                vote(
                    "1",
                    VotePosition.POUR,
                    groupPosition = GroupVotePosition("SOC", VotePosition.POUR, true, forCount = 8, againstCount = 2, abstentionCount = 0, nonVotingCount = 0),
                    voteBreakdown = VoteBreakdown(totalVoters = 500, forCount = 300, againstCount = 180, abstentionCount = 20, nonVotingCount = 77, absoluteMajority = null, resultLabel = "adopté")
                ),
                vote(
                    "2",
                    VotePosition.CONTRE,
                    groupPosition = GroupVotePosition("SOC", VotePosition.POUR, false, forCount = 6, againstCount = 2, abstentionCount = 0, nonVotingCount = 2),
                    voteBreakdown = VoteBreakdown(totalVoters = 400, forCount = 180, againstCount = 200, abstentionCount = 20, nonVotingCount = 177, absoluteMajority = null, resultLabel = "rejeté")
                )
            )
        )

        assertEquals(2, stats.groupAlignmentComparableVotes)
        assertEquals(1, stats.groupAlignedVotes)
        assertEquals(50, stats.groupAlignmentPercent)
        assertEquals(90, stats.groupAverageParticipationPercent)
        assertEquals(78, stats.assemblyAverageParticipationPercent)
    }

    private fun vote(id: String, position: VotePosition, groupPosition: GroupVotePosition? = null, voteBreakdown: VoteBreakdown? = null) = Vote(
        id = id,
        number = id,
        date = "2026-05-${id.padStart(2, '0')}",
        title = "Scrutin $id",
        result = "Adopté",
        summary = "Résumé",
        deputePosition = position,
        sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/$id",
        voteBreakdown = voteBreakdown,
        groupPosition = groupPosition
    )
}
