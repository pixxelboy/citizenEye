package com.citizeneye.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialVoteEnrichmentRepositoryTest {
    @Test fun parsesParentTextDetailsFromDossierJson() {
        val dossier = JSONObject(
            """
            {
              "uid":"DLR5L17N54083",
              "legislature":"17",
              "titreDossier":{"titre":"Projet de loi actualisant la programmation militaire"},
              "procedureParlementaire":{"libelle":"Projet de loi ordinaire"},
              "actesLegislatifs":{"acteLegislatif":[
                {"codeActe":"AN1-DEPOT","libelleActe":{"libelleCourt":"Dépôt"},"texteAssocie":"PRJLANR5L17B2630"},
                {"codeActe":"AN1-COM-FOND-NOMIN","organeRef":"PO59046","libelleActe":{"libelleCourt":"Nomination de rapporteur"},"rapporteurs":{"rapporteur":[{"acteurRef":"PA795908"}]}},
                {"codeActe":"AN1-SEANCE","libelleActe":{"libelleCourt":"Séance publique"}}
              ]}
            }
            """.trimIndent()
        )

        val parent = parseParentTextDetails(dossier)

        assertEquals("Projet de loi actualisant la programmation militaire", parent.title)
        assertEquals("Projet de loi ordinaire", parent.type)
        assertEquals("17", parent.legislature)
        assertEquals("2630", parent.depositNumber)
        assertEquals(listOf("PA795908"), parent.rapporteurs)
        assertEquals("Séance publique", parent.procedureStage)
        assertNotNull(parent.dossierUrl)
    }

    @Test fun enrichesAmendmentDetailsFromOfficialVoteObject() = runBlocking {
        val repository = AssembleeOfficialVoteEnrichmentRepository(publicDataCache = null)
        val vote = fakeVote(
            title = "L'amendement n° 299 de M. Lachaud à l'article 23 du projet de loi actualisant la programmation militaire (première lecture).",
            objectTitle = "l'amendement n° 299 de M. Lachaud à l'article 23 du projet de loi actualisant la programmation militaire",
            dossierRef = "DLR5L17N54083",
            dossierTitle = "Projet de loi actualisant la programmation militaire"
        )

        val amendment = repository.findAmendmentDetailsForVote(vote)

        assertNotNull(amendment)
        assertEquals("299", amendment?.number)
        assertTrue(amendment?.authors?.contains("M. Lachaud") == true)
        assertEquals(vote.result, amendment?.status)
        assertEquals(vote.sourceUrl, amendment?.sourceUrl)
    }

    @Test fun enrichesArticleAndMotionDetailsFromOfficialVoteObject() = runBlocking {
        val repository = AssembleeOfficialVoteEnrichmentRepository(publicDataCache = null)
        val article = repository.findArticleDetailsForVote(fakeVote(title = "L'article 22 du projet de loi de programmation pour Mayotte.", objectTitle = "l'article 22 du projet de loi"))
        val motion = repository.findMotionDetailsForVote(fakeVote(title = "La motion de censure déposée en application de l'article 49.", objectTitle = "la motion de censure"))

        assertEquals("22", article?.number)
        assertEquals("la motion de censure", motion?.explanation)
        assertEquals("No-confidence motion", motion?.type)
    }

    private fun fakeVote(
        title: String,
        objectTitle: String? = null,
        dossierRef: String? = null,
        dossierTitle: String? = null
    ) = Vote(
        id = "VTANR5L17V6706",
        number = "6706",
        date = "2026-05-01",
        title = title,
        result = "l'Assemblée nationale a adopté",
        summary = "Source : Assemblée nationale.",
        deputePosition = VotePosition.POUR,
        sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/6706",
        objectTitle = objectTitle,
        dossierRef = dossierRef,
        dossierTitle = dossierTitle,
        seanceRef = "RUANR5L17S2026IDS1"
    )
}
