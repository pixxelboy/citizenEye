package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeputyDisplayFormattingTest {
    @Test fun compactPoliticalGroupUsesAbbreviationByDefault() {
        val depute = Depute(
            id = "PA1",
            name = "Députée Test",
            group = "SOC",
            departmentName = "Paris",
            departmentCode = "75",
            constituencyNumber = "1",
            email = null,
            politicalGroupFullName = "Socialistes et apparentés",
            politicalGroupAbbreviation = "SOC"
        )

        assertEquals("SOC", depute.group)
        assertEquals("SOC", depute.displayPoliticalGroupShort)
        assertEquals("Socialistes et apparentés", depute.displayPoliticalGroupFull)
    }

    @Test fun missingDeputyProfileFieldsHaveNeutralFallbacks() {
        val depute = Depute(
            id = "PA1",
            name = "Députée Test",
            group = "N/R",
            departmentName = "",
            departmentCode = "",
            constituencyNumber = "1",
            email = null,
            regionName = null,
            profession = null,
            politicalGroupFullName = "",
            politicalGroupAbbreviation = ""
        )

        assertEquals("N/R", depute.displayPoliticalGroupShort)
        assertEquals("Groupe non renseigné", depute.displayPoliticalGroupFull)
        assertEquals("Non renseignée", depute.displayRegion)
        assertEquals("Non renseignée", depute.displayProfession)
        assertEquals("Non renseigné", depute.displayDepartment)
    }

    @Test fun departmentDisplayIncludesCodeWhenAvailable() {
        val depute = Depute(
            id = "PA1",
            name = "Députée Test",
            group = "ENS",
            departmentName = "Hauts-de-Seine",
            departmentCode = "92",
            constituencyNumber = "4",
            email = null,
            regionName = "Île-de-France",
            profession = "Avocate",
            politicalGroupFullName = "Ensemble pour la République",
            politicalGroupAbbreviation = "ENS"
        )

        assertEquals("Hauts-de-Seine (92)", depute.displayDepartment)
        assertEquals("Île-de-France", depute.displayRegion)
        assertEquals("Avocate", depute.displayProfession)
    }
}
