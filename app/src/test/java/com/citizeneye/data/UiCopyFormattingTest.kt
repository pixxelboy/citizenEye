package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCopyFormattingTest {

    @Test fun technicalIdsAreDetected() {
        assertTrue(isTechnicalId("PO59051"))
        assertTrue(isTechnicalId("PA720976"))
        assertFalse(isTechnicalId("Commission des lois"))
        assertFalse(isTechnicalId("Mme Sabrina Sebaihi"))
    }

    @Test fun safeDisplayValueHidesMissingAndTechnicalValues() {
        assertNull(safeDisplayValue(null))
        assertNull(safeDisplayValue(""))
        assertNull(safeDisplayValue("   "))
        assertNull(safeDisplayValue("PO59051"))
        assertEquals("Commission des affaires sociales", safeDisplayValue("Commission des affaires sociales"))
    }

    @Test fun positionInContextExplainsVoteObject() {
        assertEquals("Pour l’amendement", formatPositionInContext(VotePosition.POUR, VoteSubjectType.AMENDMENT))
        assertEquals("Contre l’amendement", formatPositionInContext(VotePosition.CONTRE, VoteSubjectType.AMENDMENT))
        assertEquals("Pour le texte", formatPositionInContext(VotePosition.POUR, VoteSubjectType.FULL_TEXT))
        assertEquals("Abstention", formatPositionInContext(VotePosition.ABSTENTION, VoteSubjectType.AMENDMENT))
        assertEquals("Non-votant", formatPositionInContext(VotePosition.NON_VOTANT, VoteSubjectType.FULL_TEXT))
    }

    @Test fun emailSubjectUsesPublicVoteNumber() {
        assertEquals("Question sur votre vote au scrutin public n°6994", buildEmailSubject("6994"))
    }

    @Test fun emailBodyIsRespectfulSourcedAndDoesNotAssumeDisagreement() {
        val body = buildEmailBody(
            voteNumber = "6994",
            voteDate = "28 mai 2026",
            officialVoteTitle = "Scrutin public sur l’amendement n°12",
            deputyPosition = "Pour l’amendement",
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/6994",
            includeDisagreement = false
        )

        assertTrue(body.contains("scrutin public n°6994"))
        assertTrue(body.contains("28 mai 2026"))
        assertTrue(body.contains("Scrutin public sur l’amendement n°12"))
        assertTrue(body.contains("Pour l’amendement"))
        assertTrue(body.contains("https://www.assemblee-nationale.fr/dyn/17/scrutins/6994"))
        assertTrue(body.contains("Je souhaite mieux comprendre"))
        assertFalse(body.contains("Je ne me sens pas aligné"))
        assertFalse(body.contains("scandale"))
    }

    @Test fun emailBodyCanIncludeExplicitDisagreementOnlyWhenRequested() {
        val body = buildEmailBody(
            voteNumber = "6994",
            voteDate = "28 mai 2026",
            officialVoteTitle = "Scrutin public sur l’amendement n°12",
            deputyPosition = "Pour l’amendement",
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/6994",
            includeDisagreement = true
        )

        assertTrue(body.contains("Je ne me sens pas aligné avec cette position"))
    }

    @Test fun plainVoteResultLabelsAreShort() {
        assertEquals("Amendement rejeté", formatVoteResultLabel(VoteSubjectType.AMENDMENT, "rejeté"))
        assertEquals("Amendement adopté", formatVoteResultLabel(VoteSubjectType.AMENDMENT, "adopté"))
        assertEquals("Texte adopté", formatVoteResultLabel(VoteSubjectType.FULL_TEXT, "adopté"))
    }
}
