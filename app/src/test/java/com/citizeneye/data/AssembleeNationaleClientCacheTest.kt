package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class AssembleeNationaleClientCacheTest {
    @Test fun cachesVotesForSameDeputyWithinOneDay() {
        var now = 1_000_000L
        var calls = 0
        val staticClient = object : StaticCitizenEyeDatasetClient(Files.createTempDirectory("static-votes-cache").toFile()) {
            override fun fetchLegislatureVotesFor(actorId: String): List<Vote> {
                calls += 1
                return listOf(fakeVote(actorId, calls))
            }
        }
        val client = AssembleeNationaleClient(nowMillis = { now }, staticDatasetClient = staticClient)

        assertEquals("PA1-1", client.fetchLegislatureVotesFor("PA1").single().id)
        assertEquals("PA1-1", client.fetchLegislatureVotesFor("PA1").single().id)
        assertEquals(1, calls)

        now += PublicDataCache.ONE_DAY_MILLIS + 1

        assertEquals("PA1-2", client.fetchLegislatureVotesFor("PA1").single().id)
        assertEquals(2, calls)
    }

    private fun fakeVote(actorId: String, call: Int) = Vote(
        id = "$actorId-$call",
        number = call.toString(),
        date = "2026-01-0$call",
        title = "Scrutin test",
        result = "Adopté",
        summary = "Résumé",
        deputePosition = VotePosition.POUR,
        sourceUrl = "https://assemblee.test/$call"
    )
}
