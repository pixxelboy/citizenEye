package com.citizeneye.data

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CivicEmailTest {
    private val depute = Depute(
        id = "PA1",
        name = "Camille Martin",
        group = "EPR",
        departmentName = "Paris",
        departmentCode = "75",
        constituencyNumber = "1",
        email = "camille.martin@assemblee-nationale.fr"
    )

    @Test
    fun pastVoteExplanationEmailIncludesVoteFactsAndSource() {
        val vote = Vote(
            id = "civix:scrutin:1843",
            number = "1843",
            date = "2026-04-12",
            title = "Scrutin public sur l’ensemble du projet de loi relatif au logement",
            result = "Adopté",
            summary = "Résumé public",
            deputePosition = VotePosition.POUR,
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/1843"
        )

        val draft = buildCivicEmailDraft(
            context = CivicEmailContext.PastVote(vote, depute),
            intent = CivicEmailIntent.ASK_EXPLANATION
        )

        assertNotEquals("", draft.subject)
        assertTrue(draft.subject.contains("2026-04-12"))
        assertTrue(draft.body.contains(vote.title))
        assertTrue(draft.body.contains(vote.date))
        assertTrue(draft.body.contains("Pour"))
        assertTrue(draft.body.contains("Adopté"))
        assertTrue(draft.body.contains(vote.sourceUrl))
        assertTrue(draft.body.contains("comprendre les raisons"))
        assertTrue(draft.body.contains("Je vous écris en tant que citoyen/ne concerné/e par ce sujet."))
    }

    @Test
    fun upcomingVoteArgumentEmailIncludesTextStageDateAndSource() {
        val vote = UpcomingVote(
            id = "upcoming-1",
            title = "Proposition de loi visant à renforcer la transparence publique",
            shortSummary = "Résumé court",
            citizenSummary = "Résumé citoyen",
            currentStage = "Examen en séance publique",
            status = UpcomingVoteStatus.UNDER_DISCUSSION,
            expectedDateLabel = "Semaine du 15 avril",
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/dossiers/transparence"
        )

        val draft = buildCivicEmailDraft(
            context = CivicEmailContext.UpcomingVote(vote, depute),
            intent = CivicEmailIntent.ARGUE_FOR
        )

        assertNotEquals("", draft.subject)
        assertTrue(draft.subject.contains("À propos du texte à venir"))
        assertTrue(draft.body.contains(vote.title))
        assertTrue(draft.body.contains(vote.currentStage))
        assertTrue(draft.body.contains(vote.status.label))
        assertTrue(draft.body.contains(vote.expectedDateLabel))
        assertTrue(draft.body.contains(vote.sourceUrl))
        assertTrue(draft.body.contains("prendre en compte"))
    }

    @Test
    fun missingOptionalFieldsDoNotCrashAndSubjectsStayNonEmpty() {
        val pastVote = Vote(
            id = "vote-minimal",
            number = "",
            date = "",
            title = "",
            result = "",
            summary = "",
            deputePosition = VotePosition.NON_VOTANT,
            sourceUrl = ""
        )
        val upcomingVote = UpcomingVote(
            id = "upcoming-minimal",
            title = "",
            shortSummary = "",
            citizenSummary = "",
            currentStage = "",
            status = UpcomingVoteStatus.AGENDA_ITEM,
            expectedDateLabel = "",
            sourceUrl = ""
        )

        CivicEmailIntent.entries.forEach { intent ->
            if (intent.supports(CivicEmailContext.PastVote(pastVote, depute))) {
                val draft = buildCivicEmailDraft(CivicEmailContext.PastVote(pastVote, depute), intent)
                assertTrue(draft.subject.isNotBlank())
                assertTrue(draft.body.contains("Non renseigné"))
            }
            if (intent.supports(CivicEmailContext.UpcomingVote(upcomingVote, depute))) {
                val draft = buildCivicEmailDraft(CivicEmailContext.UpcomingVote(upcomingVote, depute), intent)
                assertTrue(draft.subject.isNotBlank())
                assertTrue(draft.body.contains("Non renseigné"))
            }
        }
    }
}
