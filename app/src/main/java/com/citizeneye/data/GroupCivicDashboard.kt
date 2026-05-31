package com.citizeneye.data

import kotlin.math.roundToInt

data class GroupCivicDashboard(
    val groupAbbreviation: String,
    val groupName: String,
    val deputyCount: Int,
    val assemblySeatCount: Int,
    val assemblySharePercent: Int,
    val voteDistribution: GroupVoteDistribution,
    val disciplinePercent: Int,
    val alignedVotes: Int,
    val dissidentVotes: Int,
    val presencePercent: Int,
    val votesParticipated: Int,
    val influencePercent: Int,
    val wonVotes: Int,
    val lostVotes: Int,
    val topTopics: List<PolicyTopicDistribution>,
    val politicalProximities: List<GroupPoliticalProximity>,
    val mostIndependentDeputies: List<GroupDeputyIndependence>,
    val members: List<GroupMemberSummary>
)

data class GroupVoteDistribution(
    val pourCount: Int,
    val contreCount: Int,
    val abstentionCount: Int,
    val absentCount: Int
) {
    val total: Int get() = pourCount + contreCount + abstentionCount + absentCount
    val pourPercent: Int get() = percentOf(pourCount, total)
    val contrePercent: Int get() = percentOf(contreCount, total)
    val abstentionPercent: Int get() = percentOf(abstentionCount, total)
    val absentPercent: Int get() = percentOf(absentCount, total)
}

data class GroupPoliticalProximity(
    val groupAbbreviation: String,
    val groupName: String,
    val sharedVotingRatePercent: Int,
    val comparedVotes: Int
)

data class GroupDeputyIndependence(
    val depute: Depute,
    val dissidentPercent: Int,
    val dissidentVotes: Int,
    val comparableVotes: Int
)

data class GroupMemberSummary(
    val depute: Depute,
    val presencePercent: Int,
    val activityCount: Int,
    val alignmentPercent: Int
)

fun buildGroupCivicDashboard(
    groupAbbreviation: String,
    allDeputies: List<Depute>,
    votesByDeputy: Map<String, List<Vote>>
): GroupCivicDashboard {
    val normalizedGroup = groupAbbreviation.trim()
    val members = allDeputies.filter { it.displayPoliticalGroupShort == normalizedGroup || it.group == normalizedGroup }
        .sortedBy { it.name }
    val groupVotes = members.flatMap { votesByDeputy[it.id].orEmpty() }
    val distribution = GroupVoteDistribution(
        pourCount = groupVotes.count { it.deputePosition == VotePosition.POUR },
        contreCount = groupVotes.count { it.deputePosition == VotePosition.CONTRE },
        abstentionCount = groupVotes.count { it.deputePosition == VotePosition.ABSTENTION },
        absentCount = groupVotes.count { it.deputePosition == VotePosition.NON_VOTANT }
    )
    val comparable = groupVotes.filter { it.groupAlignment != VoteGroupAlignment.UNKNOWN }
    val aligned = comparable.count { it.groupAlignment == VoteGroupAlignment.ALIGNED }
    val dissident = comparable.count { it.groupAlignment == VoteGroupAlignment.DISSIDENT }
    val uniqueGroupVotes = groupVotes.distinctBy { it.id }
    val wins = uniqueGroupVotes.count { vote -> vote.groupMajorityWon() }
    val losses = uniqueGroupVotes.count { vote -> vote.groupMajorityLost() }
    val groupVoteMajorities = votesByDeputy
        .filterKeys { id -> members.any { it.id == id } }
        .values.flatten()
        .groupBy { it.id }
        .mapValues { (_, votes) -> votes.majorityPosition() }
    val groupByDeputyGroup = allDeputies.groupBy { it.displayPoliticalGroupShort }
    val proximities = groupByDeputyGroup
        .filterKeys { it.isNotBlank() && it != normalizedGroup }
        .mapNotNull { (otherGroup, otherDeputies) ->
            val otherVoteMajorities = otherDeputies.flatMap { votesByDeputy[it.id].orEmpty() }
                .groupBy { it.id }
                .mapValues { (_, votes) -> votes.majorityPosition() }
            val sharedIds = groupVoteMajorities.keys.intersect(otherVoteMajorities.keys)
            val comparableIds = sharedIds.filter { groupVoteMajorities[it] != null && otherVoteMajorities[it] != null }
            if (comparableIds.isEmpty()) null else {
                val shared = comparableIds.count { groupVoteMajorities[it] == otherVoteMajorities[it] }
                GroupPoliticalProximity(
                    groupAbbreviation = otherGroup,
                    groupName = otherDeputies.firstOrNull()?.displayPoliticalGroupFull ?: otherGroup,
                    sharedVotingRatePercent = percentOf(shared, comparableIds.size),
                    comparedVotes = comparableIds.size
                )
            }
        }
        .sortedWith(compareByDescending<GroupPoliticalProximity> { it.sharedVotingRatePercent }.thenByDescending { it.comparedVotes })
    val memberSummaries = members.map { depute ->
        val votes = votesByDeputy[depute.id].orEmpty()
        val memberComparable = votes.filter { it.groupAlignment != VoteGroupAlignment.UNKNOWN }
        val memberAligned = memberComparable.count { it.groupAlignment == VoteGroupAlignment.ALIGNED }
        val memberDissident = memberComparable.count { it.groupAlignment == VoteGroupAlignment.DISSIDENT }
        GroupMemberSummary(
            depute = depute,
            presencePercent = percentOf(votes.count { it.deputePosition != VotePosition.NON_VOTANT }, votes.size),
            activityCount = votes.size,
            alignmentPercent = percentOf(memberAligned, memberComparable.size)
        ) to GroupDeputyIndependence(
            depute = depute,
            dissidentPercent = percentOf(memberDissident, memberComparable.size),
            dissidentVotes = memberDissident,
            comparableVotes = memberComparable.size
        )
    }
    val memberRows = memberSummaries.map { it.first }
    val independents = memberSummaries.map { it.second }
        .filter { it.comparableVotes > 0 && it.dissidentVotes > 0 }
        .sortedWith(compareByDescending<GroupDeputyIndependence> { it.dissidentPercent }.thenByDescending { it.dissidentVotes }.thenBy { it.depute.name })
        .take(10)
    return GroupCivicDashboard(
        groupAbbreviation = normalizedGroup,
        groupName = members.firstOrNull()?.displayPoliticalGroupFull ?: normalizedGroup,
        deputyCount = members.size,
        assemblySeatCount = allDeputies.size,
        assemblySharePercent = percentOf(members.size, allDeputies.size.coerceAtLeast(1)),
        voteDistribution = distribution,
        disciplinePercent = percentOf(aligned, comparable.size),
        alignedVotes = aligned,
        dissidentVotes = dissident,
        presencePercent = percentOf(groupVotes.count { it.deputePosition != VotePosition.NON_VOTANT }, groupVotes.size),
        votesParticipated = uniqueGroupVotes.size,
        influencePercent = percentOf(wins, wins + losses),
        wonVotes = wins,
        lostVotes = losses,
        topTopics = groupVotes.policyTopicDistribution().take(5),
        politicalProximities = proximities,
        mostIndependentDeputies = independents,
        members = memberRows
    )
}

private fun List<Vote>.majorityPosition(): VotePosition? = groupBy { it.deputePosition }
    .filterKeys { it != VotePosition.NON_VOTANT }
    .maxWithOrNull(compareBy<Map.Entry<VotePosition, List<Vote>>> { it.value.size }.thenBy { -it.key.ordinal })
    ?.key

private fun Vote.groupMajorityWon(): Boolean {
    val majority = groupPosition?.groupMajorityPosition ?: return false
    val label = result.lowercase()
    return (label.contains("adopt") && majority == VotePosition.POUR) ||
        ((label.contains("rejet") || label.contains("repouss")) && majority == VotePosition.CONTRE)
}

private fun Vote.groupMajorityLost(): Boolean {
    val majority = groupPosition?.groupMajorityPosition ?: return false
    val label = result.lowercase()
    return (label.contains("adopt") && majority == VotePosition.CONTRE) ||
        ((label.contains("rejet") || label.contains("repouss")) && majority == VotePosition.POUR)
}

private fun percentOf(value: Int, total: Int): Int = if (total <= 0) 0 else ((value.toDouble() / total.toDouble()) * 100).roundToInt()
