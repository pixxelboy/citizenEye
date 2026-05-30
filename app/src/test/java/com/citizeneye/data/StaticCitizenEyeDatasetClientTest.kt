package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class StaticCitizenEyeDatasetClientTest {
    @Test fun parsesManifestAndLoadsDeputiesVotesAndDossiers() {
        val root = Files.createTempDirectory("citizeneye-static").toFile()
        val deputies = gz("""
            {"schemaVersion":1,"deputies":[{"id":"PA1","name":"Députée Test","group":"SOC","departmentName":"Paris","departmentCode":"75","constituencyNumber":"1","email":"test@assemblee.fr","regionName":"Île-de-France","profession":"Avocate","politicalGroupFullName":"Socialistes et apparentés","politicalGroupAbbreviation":"SOC","photoUrl":"https://example.test/photo.jpg"}]}
        """.trimIndent())
        val votes = gz("""
            {"schemaVersion":1,"votesByDeputy":{"PA1":[{"id":"VT1","number":"12","date":"2026-01-02","title":"Scrutin public sur l’amendement n° 1","result":"Adopté","summary":"Pour 10, contre 2, abstentions 1.","deputePosition":"POUR","sourceUrl":"https://assemblee.test/12","voteBreakdown":{"totalVoters":13,"forCount":10,"againstCount":2,"abstentionCount":1,"nonVotingCount":0,"absoluteMajority":7,"resultLabel":"adopté"},"groupPosition":{"groupName":"SOC","groupMajorityPosition":"POUR","deputyVotedLikeGroup":true,"forCount":9,"againstCount":1,"abstentionCount":0,"nonVotingCount":0},"objectTitle":"Objet","dossierRef":"DL1","dossierTitle":"Dossier","legislativeReference":"ART. 1","seanceRef":"RU1"}]}}
        """.trimIndent())
        val dossiers = gz("""
            {"schemaVersion":1,"dossiersByRef":{"DL1":{"title":"Dossier","type":"Projet de loi","procedureStage":"1ère lecture","legislature":"17","dossierUrl":"https://assemblee.test/dossier","commissionName":"Commission","rapporteurs":["PA2"],"depositNumber":"123","adoptionStatus":"adopté"}}}
        """.trimIndent())
        val manifest = manifest(deputies, votes, dossiers, version = "v1")
        val files = mapOf(
            "https://pages.test/manifest.json" to manifest.toByteArray(),
            "https://pages.test/deputies-${sha(deputies)}.json.gz" to deputies,
            "https://pages.test/votes-${sha(votes)}.json.gz" to votes,
            "https://pages.test/dossiers-${sha(dossiers)}.json.gz" to dossiers
        )
        val client = StaticCitizenEyeDatasetClient(root, "https://pages.test/") { files[it] ?: error("unexpected $it") }

        val loadedDeputies = client.fetchActiveDeputies()
        val loadedVotes = client.fetchLegislatureVotesFor("PA1")
        val parent = client.findParentText("DL1")

        assertEquals("Députée Test", loadedDeputies.single().name)
        assertEquals("SOC", loadedDeputies.single().displayPoliticalGroupShort)
        assertEquals("Socialistes et apparentés", loadedDeputies.single().displayPoliticalGroupFull)
        assertEquals(VotePosition.POUR, loadedVotes.single().deputePosition)
        assertEquals(10, loadedVotes.single().voteBreakdown?.forCount)
        assertEquals("Dossier", parent?.title)
    }

    @Test fun rejectsDownloadedFileWhoseShaDoesNotMatchManifest() {
        val root = Files.createTempDirectory("citizeneye-static-corrupt").toFile()
        val deputies = gz("{\"schemaVersion\":1,\"deputies\":[]}")
        val votes = gz("{\"schemaVersion\":1,\"votesByDeputy\":{}}")
        val dossiers = gz("{\"schemaVersion\":1,\"dossiersByRef\":{}}")
        val corrupt = deputies + byteArrayOf(1)
        val manifest = manifest(deputies, votes, dossiers, version = "v1")
        val files = mapOf(
            "https://pages.test/manifest.json" to manifest.toByteArray(),
            "https://pages.test/deputies-${sha(deputies)}.json.gz" to corrupt,
            "https://pages.test/votes-${sha(votes)}.json.gz" to votes,
            "https://pages.test/dossiers-${sha(dossiers)}.json.gz" to dossiers
        )
        val client = StaticCitizenEyeDatasetClient(root, "https://pages.test/") { files[it] ?: error("unexpected $it") }

        assertThrows(IllegalStateException::class.java) { client.fetchActiveDeputies() }
    }

    @Test fun usesStaleStaticCacheWhenNetworkFails() {
        val root = Files.createTempDirectory("citizeneye-static-stale").toFile()
        val deputies = gz("{\"schemaVersion\":1,\"deputies\":[{\"id\":\"PA1\",\"name\":\"Cache\",\"group\":\"N/R\",\"departmentName\":\"Paris\",\"departmentCode\":\"75\",\"constituencyNumber\":\"1\",\"politicalGroupFullName\":\"Groupe non renseigné\",\"politicalGroupAbbreviation\":\"N/R\"}]}")
        val votes = gz("{\"schemaVersion\":1,\"votesByDeputy\":{}}")
        val dossiers = gz("{\"schemaVersion\":1,\"dossiersByRef\":{}}")
        val manifest = manifest(deputies, votes, dossiers, version = "v1")
        var fail = false
        val files = mapOf(
            "https://pages.test/manifest.json" to manifest.toByteArray(),
            "https://pages.test/deputies-${sha(deputies)}.json.gz" to deputies,
            "https://pages.test/votes-${sha(votes)}.json.gz" to votes,
            "https://pages.test/dossiers-${sha(dossiers)}.json.gz" to dossiers
        )
        val client = StaticCitizenEyeDatasetClient(root, "https://pages.test/") { url -> if (fail) error("offline") else files[url] ?: error("unexpected $url") }

        assertEquals("Cache", client.fetchActiveDeputies().single().name)
        fail = true

        assertEquals("Cache", client.fetchActiveDeputies().single().name)
    }

    private fun manifest(deputies: ByteArray, votes: ByteArray, dossiers: ByteArray, version: String): String = """
        {"schemaVersion":1,"dataset":"citizeneye-assemblee-v17","generatedAt":"2026-01-01T00:00:00Z","version":"$version","files":[{"name":"deputies","url":"deputies-${sha(deputies)}.json.gz","sha256":"${sha(deputies)}","bytes":${deputies.size}},{"name":"votes","url":"votes-${sha(votes)}.json.gz","sha256":"${sha(votes)}","bytes":${votes.size}},{"name":"dossiers","url":"dossiers-${sha(dossiers)}.json.gz","sha256":"${sha(dossiers)}","bytes":${dossiers.size}}]}
    """.trimIndent()

    private fun gz(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
