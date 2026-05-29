package com.citizeneye.data

import java.io.File

class PublicDataCache(
    private val root: File,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val downloader: (String) -> ByteArray = ::httpGetBytes
) {
    fun getBytes(url: String, key: String): ByteArray {
        root.mkdirs()
        val cacheFile = File(root, key)
        val ageMillis = nowMillis() - cacheFile.lastModified()

        if (cacheFile.exists() && ageMillis in 0 until ONE_DAY_MILLIS) {
            return cacheFile.readBytes()
        }

        return runCatching {
            downloader(url).also { bytes ->
                val temporary = File(root, "$key.tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(cacheFile)) {
                    cacheFile.writeBytes(bytes)
                    temporary.delete()
                }
                cacheFile.setLastModified(nowMillis())
            }
        }.getOrElse { error ->
            if (cacheFile.exists()) {
                cacheFile.readBytes()
            } else {
                throw error
            }
        }
    }

    companion object {
        const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
