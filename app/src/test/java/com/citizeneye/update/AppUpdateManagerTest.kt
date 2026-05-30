package com.citizeneye.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test fun parsesValidUpdateManifest() {
        val manifest = UpdateManifestParser.parse(
            """
            {
              "versionCode": 7,
              "versionName": "1.4.2",
              "apkUrl": "https://github.com/pixxelboy/citizenEye/releases/download/v1.4.2/citizeneye-1.4.2.apk",
              "releaseNotes": ["Navigation plus claire", "Correctifs"],
              "mandatory": false,
              "publishedAt": "2026-05-30T12:00:00Z",
              "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
            """.trimIndent()
        )

        assertNotNull(manifest)
        manifest!!
        assertEquals(7L, manifest.versionCode)
        assertEquals("1.4.2", manifest.versionName)
        assertEquals(2, manifest.releaseNotes.size)
        assertFalse(manifest.mandatory)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", manifest.sha256)
    }

    @Test fun rejectsInvalidManifestWithoutHttpsApk() {
        assertNull(
            UpdateManifestParser.parse(
                """
                {
                  "versionCode": 2,
                  "versionName": "1.0.1",
                  "apkUrl": "http://example.com/app.apk",
                  "releaseNotes": [],
                  "mandatory": false,
                  "publishedAt": "2026-05-30T12:00:00Z"
                }
                """.trimIndent()
            )
        )
    }

    @Test fun rejectsInvalidManifestWithoutPositiveVersionCode() {
        assertNull(UpdateManifestParser.parse("{\"versionCode\":0,\"versionName\":\"1.0.0\",\"apkUrl\":\"https://example.com/app.apk\"}"))
    }

    @Test fun comparesVersionCodesOnly() {
        assertFalse(UpdatePolicy.isUpdateAvailable(remoteVersionCode = 4, currentVersionCode = 4))
        assertFalse(UpdatePolicy.isUpdateAvailable(remoteVersionCode = 3, currentVersionCode = 4))
        assertTrue(UpdatePolicy.isUpdateAvailable(remoteVersionCode = 5, currentVersionCode = 4))
    }

    @Test fun ignoresMalformedShaButKeepsManifestValid() {
        val manifest = UpdateManifestParser.parse(
            """
            {
              "versionCode": 2,
              "versionName": "1.0.1",
              "apkUrl": "https://example.com/app.apk",
              "releaseNotes": ["A"],
              "mandatory": true,
              "publishedAt": "2026-05-30T12:00:00Z",
              "sha256": "bad"
            }
            """.trimIndent()
        )

        assertNotNull(manifest)
        assertEquals(true, manifest!!.mandatory)
        assertNull(manifest.sha256)
    }
}
