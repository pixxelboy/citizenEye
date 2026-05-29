package com.citizeneye.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PublicDataCacheTest {
    @Test fun usesLocalPublicDataWithinOneDayWithoutRefetching() {
        val root = Files.createTempDirectory("citizeneye-cache").toFile()
        var now = 1_000_000L
        var downloads = 0
        val cache = PublicDataCache(root = root, nowMillis = { now }) { url ->
            downloads += 1
            "network-$url".toByteArray()
        }

        val first = cache.getBytes("https://example.test/deputies.zip", "deputies.zip")
        now += PublicDataCache.ONE_DAY_MILLIS - 1
        val second = cache.getBytes("https://example.test/deputies.zip", "deputies.zip")

        assertArrayEquals(first, second)
        assertEquals(1, downloads)
    }

    @Test fun refreshesPublicDataAfterOneDay() {
        val root = Files.createTempDirectory("citizeneye-cache").toFile()
        var now = 1_000_000L
        var downloads = 0
        val cache = PublicDataCache(root = root, nowMillis = { now }) { _ ->
            downloads += 1
            "network-$downloads".toByteArray()
        }

        val first = cache.getBytes("https://example.test/scrutins.zip", "votes.zip")
        now += PublicDataCache.ONE_DAY_MILLIS + 1
        val second = cache.getBytes("https://example.test/scrutins.zip", "votes.zip")

        assertEquals("network-1", first.toString(Charsets.UTF_8))
        assertEquals("network-2", second.toString(Charsets.UTF_8))
        assertEquals(2, downloads)
    }

    @Test fun keepsStaleLocalPublicDataWhenDailyRefreshFails() {
        val root = Files.createTempDirectory("citizeneye-cache").toFile()
        var now = 1_000_000L
        var fail = false
        val cache = PublicDataCache(root = root, nowMillis = { now }) { _ ->
            if (fail) error("public source unavailable")
            "cached-public-data".toByteArray()
        }

        cache.getBytes("https://example.test/deputies.zip", "deputies.zip")
        now += PublicDataCache.ONE_DAY_MILLIS + 1
        fail = true

        val stale = cache.getBytes("https://example.test/deputies.zip", "deputies.zip")

        assertEquals("cached-public-data", stale.toString(Charsets.UTF_8))
        assertEquals(true, File(root, "deputies.zip").exists())
    }
}
