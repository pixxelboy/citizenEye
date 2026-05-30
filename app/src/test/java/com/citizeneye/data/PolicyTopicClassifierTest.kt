package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTopicClassifierTest {
    private val classifier = PolicyTopicClassifier()

    @Test fun classifiesFromOfficialDossierTitleNotVoteTitle() {
        val classification = classifier.classify(
            dossierTitle = "Projet de loi relatif au logement et à la rénovation énergétique",
            legislativeReference = "DLR5L17N49231",
            objectTitle = "Scrutin sur l'article 17"
        )

        assertEquals(PolicyTopic.HOUSING, classification.primaryTopic)
        assertTrue(classification.secondaryTopics.contains(PolicyTopic.ENERGY))
        assertTrue(classification.matchedKeywords.contains("logement"))
    }

    @Test fun doesNotUseOpaqueArticleOrAmendmentTitlesAsClassificationSource() {
        val classification = classifier.classify(
            dossierTitle = null,
            legislativeReference = null,
            objectTitle = "Amendement 256 relatif à l'immigration"
        )

        assertEquals(PolicyTopic.OTHER, classification.primaryTopic)
        assertTrue(classification.secondaryTopics.isEmpty())
        assertTrue(classification.matchedKeywords.isEmpty())
    }

    @Test fun keepsPrimaryTopicAsFirstKeywordFoundInDossierText() {
        val classification = classifier.classify(
            dossierTitle = "Proposition de loi visant à améliorer le logement et le financement de la rénovation"
        )

        assertEquals("classification=$classification", PolicyTopic.HOUSING, classification.primaryTopic)
        assertEquals(listOf(PolicyTopic.BUDGET), classification.secondaryTopics)
    }

    @Test fun aggregatesVotesAcrossPrimaryAndSecondaryTopics() {
        val summaries = listOf(
            vote("1", VotePosition.POUR, "Projet de loi relatif au logement et au budget"),
            vote("2", VotePosition.POUR, "Projet de loi relatif au logement"),
            vote("3", VotePosition.CONTRE, "Projet de loi relatif au logement"),
            vote("4", VotePosition.ABSTENTION, "Projet de loi relatif à la santé"),
            vote("5", VotePosition.NON_VOTANT, "Projet de loi relatif à la santé")
        ).topicVotingSummaries(classifier)

        val housing = summaries.first { it.topic == PolicyTopic.HOUSING }
        val budget = summaries.first { it.topic == PolicyTopic.BUDGET }
        val health = summaries.first { it.topic == PolicyTopic.HEALTH }

        assertEquals(3, housing.totalVotes)
        assertEquals(2, housing.pour)
        assertEquals(1, housing.contre)
        assertEquals("Majoritairement pour", housing.dominantPositionLabel)
        assertEquals(1, budget.totalVotes)
        assertEquals(2, health.totalVotes)
        assertEquals(1, health.abstention)
        assertEquals(1, health.nonVotant)
    }

    @Test fun deputeMatchComputesTopicSummariesOnceAndPreservesThemWhenPaginating() {
        val votes = listOf(
            vote("1", VotePosition.POUR, "Projet de loi relatif au logement"),
            vote("2", VotePosition.CONTRE, "Projet de loi relatif à la santé")
        )
        val match = DeputeMatch(
            query = "Nanterre",
            commune = Commune("Nanterre", "92050", listOf("92000"), "92"),
            depute = Depute(
                id = "PA1",
                name = "Députée Test",
                group = "N/R",
                departmentName = "Hauts-de-Seine",
                departmentCode = "92",
                constituencyNumber = "1",
                email = null
            ),
            allLegislatureVotes = votes
        )

        val paginated = match.withMoreVisibleVotes()

        assertTrue(match.topicVotingSummaries === paginated.topicVotingSummaries)
        assertTrue(match.legislatureStats === paginated.legislatureStats)
    }

    private fun vote(id: String, position: VotePosition, dossierTitle: String) = Vote(
        id = id,
        number = id,
        date = "2026-05-${id.padStart(2, '0')}",
        title = "Scrutin public sur l'article $id",
        result = "Adopté",
        summary = "Résumé",
        deputePosition = position,
        sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/$id",
        dossierTitle = dossierTitle,
        legislativeReference = "DL$id",
        objectTitle = "Article $id"
    )
}
