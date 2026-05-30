package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticalFamiliesTest {
    @Test fun mapsKnownGroupCodeToConfiguredPoliticalFamily() {
        val depute = Depute(
            id = "PA1",
            name = "Députée Test",
            group = "SOC",
            departmentName = "Nord",
            departmentCode = "59",
            constituencyNumber = "3",
            email = null,
            politicalGroupFullName = "Socialistes et apparentés",
            politicalGroupAbbreviation = "SOC"
        )

        val family = PoliticalFamilies.forDeputy(depute)

        assertEquals("SOC", family.groupCode)
        assertEquals("Centre gauche", family.politicalFamily)
        assertTrue(family.officialAssemblyUrl.contains("assemblee-nationale.fr"))
    }

    @Test fun unknownGroupKeepsOfficialLabelsWithoutInventingFamily() {
        val depute = Depute(
            id = "PA2",
            name = "Député Test",
            group = "XYZ",
            departmentName = "Nord",
            departmentCode = "59",
            constituencyNumber = "4",
            email = null,
            politicalGroupFullName = "Groupe officiel inconnu",
            politicalGroupAbbreviation = "XYZ"
        )

        val family = PoliticalFamilies.forDeputy(depute)

        assertEquals("XYZ", family.groupCode)
        assertEquals("Groupe officiel inconnu", family.groupName)
        assertEquals("Information non disponible depuis les sources officielles.", family.politicalFamily)
    }
}
