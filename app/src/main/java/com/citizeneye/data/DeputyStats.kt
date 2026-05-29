package com.citizeneye.data

import kotlin.math.roundToInt

data class DeputyStats(
    val totalVotes: Int,
    val participatedVotes: Int,
    val counts: Map<VotePosition, Int>
) {
    val participationPercent: Int get() = percent(participatedVotes, totalVotes)

    fun countFor(position: VotePosition): Int = counts[position] ?: 0

    fun percentFor(position: VotePosition): Int = percent(countFor(position), totalVotes)

    companion object {
        fun from(votes: List<Vote>): DeputyStats {
            val counts = VotePosition.entries.associateWith { position ->
                votes.count { it.deputePosition == position }
            }
            return DeputyStats(
                totalVotes = votes.size,
                participatedVotes = votes.count { it.deputePosition != VotePosition.NON_VOTANT },
                counts = counts
            )
        }

        private fun percent(value: Int, total: Int): Int {
            if (total == 0) return 0
            return ((value.toDouble() / total.toDouble()) * 100).roundToInt()
        }
    }
}
