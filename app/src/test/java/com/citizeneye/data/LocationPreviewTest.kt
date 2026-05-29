package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPreviewTest {
    private val commune = Commune("Nanterre", "92050", listOf("92000"), "92")

    @Test fun describesSingleConstituencyAsAutocompletableButNotValidated() {
        val preview = LocationPreview(
            query = "92000",
            commune = commune,
            deputies = listOf(depute("PA1", "Députée Unique", "4"))
        )

        assertFalse(preview.requiresDeputyChoice)
        assertEquals("Nanterre · département 92", preview.locationLabel)
        assertEquals("Députée Unique · 4e circonscription", preview.representativeHint)
    }

    @Test fun describesAmbiguousConstituencyAsNeedingUserChoice() {
        val preview = LocationPreview(
            query = "92000",
            commune = commune,
            deputies = listOf(depute("PA1", "Députée A", "4"), depute("PA2", "Député B", "5"))
        )

        assertTrue(preview.requiresDeputyChoice)
        assertEquals("2 circonscriptions possibles", preview.representativeHint)
    }

    private fun depute(id: String, name: String, circo: String) = Depute(
        id = id,
        name = name,
        group = "Groupe Test",
        departmentName = "Hauts-de-Seine",
        departmentCode = "92",
        constituencyNumber = circo,
        email = null
    )
}
