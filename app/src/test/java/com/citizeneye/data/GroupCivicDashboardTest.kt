package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupCivicDashboardTest {
    @Test
    fun groupDashboardAggregatesDisciplineVotesTopicsInfluenceAndIndependentDeputies() {
        val groupDeputies = listOf(deputy("PA1", "Alice"), deputy("PA2", "Bob"))
        val allDeputies = groupDeputies + deputy("PA3", "Claire", group = "SOC")
        val votesByDeputy = mapOf(
            "PA1" to listOf(
                vote("v1", "2026-05-01", VotePosition.POUR, VotePosition.POUR, pour = 90, contre = 10, dossierTitle = "Projet de loi énergie"),
                vote("v2", "2026-04-01", VotePosition.CONTRE, VotePosition.POUR, pour = 52, contre = 48, dossierTitle = "Projet de loi de finances")
            ),
            "PA2" to listOf(
                vote("v1", "2026-05-01", VotePosition.POUR, VotePosition.POUR, pour = 90, contre = 10, dossierTitle = "Projet de loi énergie"),
                vote("v2", "2026-04-01", VotePosition.POUR, VotePosition.POUR, pour = 52, contre = 48, dossierTitle = "Projet de loi de finances"),
                vote("v3", "2026-03-01", VotePosition.POUR, VotePosition.POUR, pour = 70, contre = 30, dossierTitle = "Projet de loi énergie")
            ),
            "PA3" to listOf(
                vote("v1", "2026-05-01", VotePosition.POUR, VotePosition.POUR, pour = 90, contre = 10),
                vote("v2", "2026-04-01", VotePosition.CONTRE, VotePosition.CONTRE, pour = 52, contre = 48)
            )
        )

        val dashboard = buildGroupCivicDashboard("EPR", allDeputies, votesByDeputy)

        assertEquals("EPR", dashboard.groupAbbreviation)
        assertEquals(2, dashboard.deputyCount)
        assertEquals(67, dashboard.assemblySharePercent)
        assertEquals(80, dashboard.disciplinePercent)
        assertEquals(4, dashboard.alignedVotes)
        assertEquals(1, dashboard.dissidentVotes)
        assertEquals(80, dashboard.voteDistribution.pourPercent)
        assertEquals(20, dashboard.voteDistribution.contrePercent)
        assertEquals(100, dashboard.influencePercent)
        assertEquals(PolicyTopic.ENERGY, dashboard.topTopics.first().topic)
        assertEquals("Alice", dashboard.mostIndependentDeputies.first().depute.name)
        assertTrue(dashboard.politicalProximities.any { it.groupAbbreviation == "SOC" && it.sharedVotingRatePercent == 50 })
    }

    private fun deputy(id: String, name: String, group: String = "EPR") = Depute(
        id = id,
        name = name,
        group = group,
        departmentName = "Paris",
        departmentCode = "75",
        constituencyNumber = "1",
        email = null,
        politicalGroupFullName = if (group == "EPR") "Ensemble pour la République" else "Socialistes",
        politicalGroupAbbreviation = group
    )

    private fun vote(
        id: String,
        date: String,
        deputy: VotePosition,
        group: VotePosition,
        pour: Int,
        contre: Int,
        dossierTitle: String = "Projet de loi"
    ) = Vote(
        id = id,
        number = id,
        date = date,
        title = dossierTitle,
        result = "Adopté",
        summary = "",
        deputePosition = deputy,
        sourceUrl = "https://assemblee.test/$id",
        voteBreakdown = VoteBreakdown(totalVoters = pour + contre, forCount = pour, againstCount = contre, abstentionCount = 0, nonVotingCount = 0, absoluteMajority = null, resultLabel = "Adopté"),
        groupPosition = GroupVotePosition("Groupe", group, group == deputy, null, null, null, null),
        dossierTitle = dossierTitle,
        objectTitle = dossierTitle
    )
}
