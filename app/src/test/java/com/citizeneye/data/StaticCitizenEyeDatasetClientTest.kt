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
        val requests = mutableListOf<String>()
        val client = StaticCitizenEyeDatasetClient(root, "https://pages.test/") { url ->
            requests += url
            files[url] ?: error("unexpected $url")
        }

        val loadedDeputies = client.fetchActiveDeputies()
        val loadedVotes = client.fetchLegislatureVotesFor("PA1")
        val parent = client.findParentText("DL1")

        assertEquals(
            listOf(
                "https://pages.test/manifest.json",
                "https://pages.test/deputies-${sha(deputies)}.json.gz",
                "https://pages.test/votes-${sha(votes)}.json.gz",
                "https://pages.test/dossiers-${sha(dossiers)}.json.gz"
            ),
            requests
        )
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

    @Test fun refreshesStaticManifestAfterOneDay() {
        val root = Files.createTempDirectory("citizeneye-static-refresh").toFile()
        var now = 1_000_000L
        val deputiesV1 = gz("{\"schemaVersion\":1,\"deputies\":[{\"id\":\"PA1\",\"name\":\"Cache\",\"group\":\"N/R\",\"departmentName\":\"Paris\",\"departmentCode\":\"75\",\"constituencyNumber\":\"1\"}]}")
        val deputiesV2 = gz("{\"schemaVersion\":1,\"deputies\":[{\"id\":\"PA1\",\"name\":\"Refresh\",\"group\":\"N/R\",\"departmentName\":\"Paris\",\"departmentCode\":\"75\",\"constituencyNumber\":\"1\"}]}")
        val votes = gz("{\"schemaVersion\":1,\"votesByDeputy\":{}}")
        val dossiers = gz("{\"schemaVersion\":1,\"dossiersByRef\":{}}")
        var files = mapOf(
            "https://pages.test/manifest.json" to manifest(deputiesV1, votes, dossiers, version = "v1").toByteArray(),
            "https://pages.test/deputies-${sha(deputiesV1)}.json.gz" to deputiesV1,
            "https://pages.test/votes-${sha(votes)}.json.gz" to votes,
            "https://pages.test/dossiers-${sha(dossiers)}.json.gz" to dossiers
        )
        val client = StaticCitizenEyeDatasetClient(
            root,
            "https://pages.test/",
            downloader = { url -> files[url] ?: error("unexpected $url") },
            nowMillis = { now }
        )

        assertEquals("Cache", client.fetchActiveDeputies().single().name)
        now += PublicDataCache.ONE_DAY_MILLIS + 1
        files = mapOf(
            "https://pages.test/manifest.json" to manifest(deputiesV2, votes, dossiers, version = "v2").toByteArray(),
            "https://pages.test/deputies-${sha(deputiesV2)}.json.gz" to deputiesV2,
            "https://pages.test/votes-${sha(votes)}.json.gz" to votes,
            "https://pages.test/dossiers-${sha(dossiers)}.json.gz" to dossiers
        )

        assertEquals("Refresh", client.fetchActiveDeputies().single().name)
    }

    @Test fun normalizesStaticDeputyPhotoUrlToOfficialRoundedPortrait() {
        val root = Files.createTempDirectory("citizeneye-static-photo").toFile()
        val deputies = gz("""
            {"schemaVersion":1,"deputies":[{"id":"PA841605","name":"Député Test","group":"N/R","departmentName":"Paris","departmentCode":"75","constituencyNumber":"1","photoUrl":"https://www.assemblee-nationale.fr/dyn/portraits/PA841605.jpg"}]}
        """.trimIndent())
        val votes = gz("{\"schemaVersion\":1,\"votesByDeputy\":{}}")
        val dossiers = gz("{\"schemaVersion\":1,\"dossiersByRef\":{}}")
        val manifest = manifest(deputies, votes, dossiers, version = "v1")
        val files = mapOf(
            "https://pages.test/manifest.json" to manifest.toByteArray(),
            "https://pages.test/deputies-${sha(deputies)}.json.gz" to deputies,
            "https://pages.test/votes-${sha(votes)}.json.gz" to votes,
            "https://pages.test/dossiers-${sha(dossiers)}.json.gz" to dossiers
        )
        val client = StaticCitizenEyeDatasetClient(root, "https://pages.test/") { files[it] ?: error("unexpected $it") }

        assertEquals(
            "https://www.assemblee-nationale.fr/dyn/static/tribun/17/photos/carre/841605.jpg",
            client.fetchActiveDeputies().single().photoUrl
        )
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
