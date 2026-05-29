package com.citizeneye.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DeputyOpenDataParsingTest {
    @Test fun politicalGroupMetadataUsesFullNameAndAbbreviationWithFallbacks() {
        val complete = PoliticalGroupMetadata.fromOrgane(
            JSONObject("""
                {
                  "uid": "PO1",
                  "codeType": "GP",
                  "libelle": "La France insoumise - Nouveau Front Populaire",
                  "libelleAbrev": "LFI-NFP"
                }
            """.trimIndent())
        )!!
        assertEquals("La France insoumise - Nouveau Front Populaire", complete.fullName)
        assertEquals("LFI-NFP", complete.abbreviation)

        val missingAbbreviation = PoliticalGroupMetadata.fromOrgane(JSONObject("{" +
            "\"uid\":\"PO2\",\"codeType\":\"GP\",\"libelle\":\"Groupe complet\"}"))!!
        assertEquals("Groupe complet", missingAbbreviation.fullName)
        assertEquals("Groupe complet", missingAbbreviation.abbreviation)

        val missingEverything = PoliticalGroupMetadata.fromOrgane(JSONObject("{\"uid\":\"PO3\",\"codeType\":\"GP\"}"))!!
        assertEquals("Groupe non renseigné", missingEverything.fullName)
        assertEquals("N/R", missingEverything.abbreviation)
    }

    @Test fun professionPrefersCurrentLabelThenInseeFallback() {
        assertEquals(
            "Professeur, profession scientifique",
            parseDeputyProfession(JSONObject("""
                {"libelleCourant":"Professeur, profession scientifique","socProcINSEE":{"catSocPro":"Cadres"}}
            """.trimIndent()))
        )
        assertEquals(
            "Techniciens",
            parseDeputyProfession(JSONObject("""
                {"socProcINSEE":{"catSocPro":"Techniciens","famSocPro":"Professions intermédiaires"}}
            """.trimIndent()))
        )
        assertEquals(null, parseDeputyProfession(JSONObject("{}")))
    }

    @Test fun regionFallsBackFromDepartmentCodeWhenElectionLieuOmitsRegion() {
        assertEquals(
            "Hauts-de-France",
            parseDeputyRegion(JSONObject("{\"region\":\"Hauts-de-France\",\"numDepartement\":\"62\"}"))
        )
        assertEquals(
            "Île-de-France",
            parseDeputyRegion(JSONObject("{\"numDepartement\":\"92\"}"))
        )
        assertEquals(
            "Français établis hors de France",
            parseDeputyRegion(JSONObject("{\"numDepartement\":\"099\"}"))
        )
    }
}
