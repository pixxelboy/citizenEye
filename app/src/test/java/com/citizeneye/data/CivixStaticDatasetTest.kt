package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CivixStaticDatasetTest {
    @Test
    fun readsGeneratedLocalJsonWithoutNetworkAndIndexesProfilesVotesAndGroups() {
        val root = createTempDir(prefix = "civix-static")
        try {
            File(root, "deputies.json").writeText("""
                {"schemaVersion":1,"generatedAt":"2026-04-01T00:00:00Z","source":{},"deputies":[{"id":"civix:deputy:PA1","civixId":"PA1","uid":"PA1","displayName":"Ada Martin","groupUid":"PO1","groupAbbreviation":"GRC"}]}
            """.trimIndent())
            File(root, "groups.json").writeText("""
                {"schemaVersion":1,"generatedAt":"2026-04-01T00:00:00Z","source":{},"groups":[{"id":"civix:group:PO1","civixId":"PO1","uid":"PO1","abbreviation":"GRC","label":"Groupe complet"}]}
            """.trimIndent())
            File(root, "scrutins.json").writeText("""
                {"schemaVersion":1,"generatedAt":"2026-04-01T00:00:00Z","source":{},"scrutins":[{"id":"civix:scrutin:VT1","civixId":"VT1","uid":"VT1","title":"Scrutin test","date":"2026-04-01T00:00:00Z","resultLabel":"adopté","dossierRef":"DL1"}]}
            """.trimIndent())
            File(root, "votes.json").writeText("""
                {"schemaVersion":1,"generatedAt":"2026-04-01T00:00:00Z","source":{},"votes":[{"id":"civix:vote:VT1:PA1","scrutinId":"VT1","deputyId":"civix:deputy:PA1","deputyCivixId":"PA1","groupId":"civix:group:GRC","groupAbbreviation":"GRC","position":"pour","deputyGroupAlignment":"unknown"}]}
            """.trimIndent())
            File(root, "dossiers.json").writeText("""
                {"schemaVersion":1,"generatedAt":"2026-04-01T00:00:00Z","source":{},"dossiers":[{"id":"civix:dossier:DL1","civixId":"DL1","ref":"DL1","title":"Dossier test","currentStage":null}]}
            """.trimIndent())
            File(root, "stats.json").writeText("""
                {"schemaVersion":1,"generatedAt":"2026-04-01T00:00:00Z","source":{},"stats":{"counts":{"deputies":1,"groups":1,"scrutins":1,"votes":1}}}
            """.trimIndent())

            val dataset = CivixStaticDataset(root)

            assertEquals("Ada Martin", dataset.getDeputyCivixProfile("PA1")?.displayName)
            assertEquals(1, dataset.getVotesForDeputy("civix:deputy:PA1").size)
            assertEquals(1, dataset.getVotesForScrutin("VT1").size)
            assertEquals(1, dataset.getGroupStats("GRC")?.deputyCount)
            assertEquals(1, dataset.getCivixStats().scrutinCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingFilesReturnEmptyDataInsteadOfCrashing() {
        val root = createTempDir(prefix = "civix-empty")
        try {
            val dataset = CivixStaticDataset(root)
            assertEquals(emptyList<CivixDeputy>(), dataset.getCivixDeputies())
            assertNull(dataset.getDeputyCivixProfile("PA404"))
            assertNotNull(dataset.getCivixStats())
        } finally {
            root.deleteRecursively()
        }
    }
}
