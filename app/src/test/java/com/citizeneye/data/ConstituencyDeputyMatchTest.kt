package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstituencyDeputyMatchTest {
    @Test fun exactBoundaryKeepsOnlyMatchingDeputy() {
        val deputies = listOf(
            depute("PA1", "Députée A", "92", "4"),
            depute("PA2", "Député B", "92", "5"),
            depute("PA3", "Députée C", "75", "4")
        )
        val boundary = ConstituencyBoundary("92", "4", "9204", "4ème circonscription")

        assertEquals(listOf("PA1"), deputies.matching(boundary).map { it.id })
    }

    private fun depute(id: String, name: String, department: String, circo: String) = Depute(
        id = id,
        name = name,
        group = "Groupe Test",
        departmentName = "Département Test",
        departmentCode = department,
        constituencyNumber = circo,
        email = null
    )
}
