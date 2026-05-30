package com.citizeneye.data

import kotlin.math.roundToInt

data class DeputyStats(
    val totalVotes: Int,
    val participatedVotes: Int,
    val counts: Map<VotePosition, Int>,
    val groupAlignmentComparableVotes: Int,
    val groupAlignedVotes: Int,
    val groupAverageParticipationPercent: Int?,
    val assemblyAverageParticipationPercent: Int?
) {
    val participationPercent: Int get() = percent(participatedVotes, totalVotes)
    val groupAlignmentPercent: Int get() = percent(groupAlignedVotes, groupAlignmentComparableVotes)

    fun countFor(position: VotePosition): Int = counts[position] ?: 0

    fun percentFor(position: VotePosition): Int = percent(countFor(position), totalVotes)

    companion object {
        fun from(votes: List<Vote>): DeputyStats {
            val counts = VotePosition.entries.associateWith { position ->
                votes.count { it.deputePosition == position }
            }
            val groupComparisons = votes.mapNotNull { it.groupPosition?.deputyVotedLikeGroup }
            val groupParticipationRates = votes.mapNotNull { vote ->
                vote.groupPosition?.let { group ->
                    val expressed = listOfNotNull(group.forCount, group.againstCount, group.abstentionCount).sum()
                    val total = expressed + (group.nonVotingCount ?: 0)
                    if (total > 0) expressed.toDouble() / total.toDouble() else null
                }
            }
            val assemblyParticipationRates = votes.mapNotNull { vote ->
                vote.voteBreakdown?.let { breakdown ->
                    val voters = breakdown.totalVoters
                    val nonVoting = breakdown.nonVotingCount
                    if (voters != null && nonVoting != null && voters + nonVoting > 0) {
                        voters.toDouble() / (voters + nonVoting).toDouble()
                    } else {
                        null
                    }
                }
            }
            return DeputyStats(
                totalVotes = votes.size,
                participatedVotes = votes.count { it.deputePosition != VotePosition.NON_VOTANT },
                counts = counts,
                groupAlignmentComparableVotes = groupComparisons.size,
                groupAlignedVotes = groupComparisons.count { it },
                groupAverageParticipationPercent = groupParticipationRates.averagePercentOrNull(),
                assemblyAverageParticipationPercent = assemblyParticipationRates.averagePercentOrNull()
            )
        }

        private fun percent(value: Int, total: Int): Int {
            if (total == 0) return 0
            return ((value.toDouble() / total.toDouble()) * 100).roundToInt()
        }

        private fun List<Double>.averagePercentOrNull(): Int? {
            if (isEmpty()) return null
            return (average() * 100).roundToInt()
        }
    }
}
