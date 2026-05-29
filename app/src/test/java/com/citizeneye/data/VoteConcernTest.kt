package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VoteConcernTest {
    @Test fun classifiesAmendementVotes() {
        assertEquals(
            VoteConcern.AMENDEMENT,
            classifyVoteConcern("l'amendement n° 42 à l'article 3 du projet de loi")
        )
    }

    @Test fun classifiesArticleVotes() {
        assertEquals(
            VoteConcern.ARTICLE,
            classifyVoteConcern("l'article 7 du projet de loi relatif à la santé")
        )
    }

    @Test fun classifiesFullTextVotes() {
        assertEquals(
            VoteConcern.TEXTE_COMPLET,
            classifyVoteConcern("l'ensemble du projet de loi après engagement de la procédure accélérée")
        )
    }

    @Test fun classifiesMotionVotes() {
        assertEquals(
            VoteConcern.MOTION,
            classifyVoteConcern("la motion de censure déposée en application de l'article 49 alinéa 3")
        )
    }

    @Test fun classifiesBudgetVotesBeforeGenericFullText() {
        assertEquals(
            VoteConcern.BUDGET,
            classifyVoteConcern("l'ensemble du projet de loi de finances pour 2026")
        )
    }

    @Test fun classifiesResolutionVotes() {
        assertEquals(
            VoteConcern.RESOLUTION,
            classifyVoteConcern("la proposition de résolution européenne sur la politique agricole commune")
        )
    }
}
