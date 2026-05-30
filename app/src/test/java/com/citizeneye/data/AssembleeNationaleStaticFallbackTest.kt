package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AssembleeNationaleStaticFallbackTest {
    @Test fun fallsBackToRawZipWhenStaticDeputiesAreUnavailable() {
        val zipBytes = zip(mapOf(
            "json/organe/PO1.json" to """
                {"organe":{"uid":"PO1","codeType":"GP","libelle":"Groupe complet","libelleAbrev":"GRC"}}
            """.trimIndent(),
            "json/acteur/PA1.json" to """
                {"acteur":{"uid":"PA1","etatCivil":{"ident":{"prenom":"Ada","nom":"Martin"}},"profession":{"libelleCourant":"Avocate"},"adresses":{"adresse":{"valElec":"ada@assemblee.fr"}},"mandats":{"mandat":[{"typeOrgane":"ASSEMBLEE","election":{"lieu":{"departement":"Paris","numDepartement":"75","numCirco":"1"}}},{"typeOrgane":"GP","organes":{"organeRef":"PO1"}}]}}}
            """.trimIndent()
        ))
        val cache = PublicDataCache(Files.createTempDirectory("raw-fallback").toFile()) { _ -> zipBytes }
        val staticClient = object : StaticCitizenEyeDatasetClient(Files.createTempDirectory("static-fallback").toFile()) {
            override fun fetchActiveDeputies(): List<Depute> = error("static unavailable")
        }
        val client = AssembleeNationaleClient(publicDataCache = cache, staticDatasetClient = staticClient)

        val depute = client.fetchActiveDeputies().single()

        assertEquals("Ada Martin", depute.name)
        assertEquals("GRC", depute.displayPoliticalGroupShort)
        assertEquals("Groupe complet", depute.displayPoliticalGroupFull)
        assertEquals("Île-de-France", depute.displayRegion)
        assertEquals("Avocate", depute.displayProfession)
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
