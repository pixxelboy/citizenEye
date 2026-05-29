package com.citizeneye.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteDetailTest {
    @Test fun classifiesVoteSubjectTypesFromRealisticTitles() {
        assertEquals(VoteSubjectType.AMENDMENT, classifyVoteSubjectType("scrutin public sur l'amendement n° 45"))
        assertEquals(VoteSubjectType.ARTICLE, classifyVoteSubjectType("scrutin public sur l'article 7 du projet de loi"))
        assertEquals(VoteSubjectType.NO_CONFIDENCE_MOTION, classifyVoteSubjectType("scrutin public sur la motion de censure"))
        assertEquals(VoteSubjectType.PROCEDURAL_MOTION, classifyVoteSubjectType("scrutin public sur la motion de rejet préalable"))
        assertEquals(VoteSubjectType.FULL_TEXT, classifyVoteSubjectType("vote solennel sur l'ensemble du projet de loi"))
        assertEquals(VoteSubjectType.BUDGET, classifyVoteSubjectType("scrutin public sur le projet de loi de finances pour 2026"))
        assertEquals(VoteSubjectType.RESOLUTION, classifyVoteSubjectType("scrutin public sur la proposition de résolution européenne"))
        assertEquals(VoteSubjectType.OTHER, classifyVoteSubjectType("scrutin public sur un objet parlementaire non identifié"))
    }

    @Test fun everySubjectTypeHasExplanation() {
        VoteSubjectType.entries.forEach { type ->
            assertTrue("$type should have an explanation", buildSubjectExplanation(type).isNotBlank())
        }
    }

    @Test fun explainsVoteEffectInContext() {
        assertTrue(buildVoteEffectExplanation(VoteSubjectType.AMENDMENT, VotePosition.POUR, "adopté").contains("adoption of the amendment"))
        assertTrue(buildVoteEffectExplanation(VoteSubjectType.AMENDMENT, VotePosition.CONTRE, "adopté").contains("against the adoption of the amendment"))
        assertTrue(buildVoteEffectExplanation(VoteSubjectType.FULL_TEXT, VotePosition.POUR, "adopté").contains("adopting the text"))
        assertTrue(buildVoteEffectExplanation(VoteSubjectType.NO_CONFIDENCE_MOTION, VotePosition.POUR, "rejeté").contains("no-confidence motion"))
        assertTrue(buildVoteEffectExplanation(VoteSubjectType.ARTICLE, VotePosition.ABSTENTION, "adopté").contains("neither for nor against"))
        assertTrue(buildVoteEffectExplanation(VoteSubjectType.OTHER, VotePosition.NON_VOTANT, "adopté").contains("no nominal for/against vote"))
    }

    @Test fun defaultVoteDetailRepositoryBuildsDetailFromDisplayedVote() = runBlocking {
        val vote = fakeVote()
        val depute = Depute(
            id = "PA1",
            name = "Test Député",
            group = "Groupe test",
            departmentName = "Paris",
            departmentCode = "75",
            constituencyNumber = "1",
            email = null
        )

        val detail = DefaultVoteDetailRepository(FakeExternalResourcesRepository()).getVoteDetail(vote, depute)

        assertEquals("500", detail.voteNumber)
        assertEquals(vote.title, detail.officialTitle)
        assertEquals(vote.sourceUrl, detail.sourceUrl)
        assertEquals(VoteSubjectType.AMENDMENT, detail.subjectType)
        assertTrue(detail.subjectExplanation.isNotBlank())
        assertTrue(detail.voteEffectExplanation.isNotBlank())
        assertTrue(detail.officialSources.any { it.sourceType == OfficialSourceType.PUBLIC_VOTE && it.url == vote.sourceUrl })
    }

    @Test fun fakeExternalResourcesRepositoryReturnsEmptyState() = runBlocking {
        val result = FakeExternalResourcesRepository().getExternalResources(
            ExternalResourceQuery(
                voteTitle = "Titre officiel",
                parentTextTitle = null,
                date = "2026-03-12",
                subjectType = VoteSubjectType.OTHER,
                officialKeywords = listOf("Assemblée nationale", "scrutin")
            )
        )

        assertTrue(result is ExternalResourcesState.Empty)
        assertEquals("No external source is configured yet.", (result as ExternalResourcesState.Empty).message)
    }

    private fun fakeVote() = Vote(
        id = "VTANR5L17V500",
        number = "500",
        date = "2026-03-12",
        title = "Scrutin public sur l'amendement n° 45 au projet de loi",
        result = "Adopté",
        summary = "Pour 120, contre 80, abstentions 15.",
        deputePosition = VotePosition.POUR,
        sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/500"
    )
}
