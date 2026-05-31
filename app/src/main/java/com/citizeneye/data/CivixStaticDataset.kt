package com.citizeneye.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CivixStaticDataset(private val root: File) {
    fun getCivixDeputies(): List<CivixDeputy> = readArray("deputies.json", "deputies") { it.toCivixDeputy() }

    fun getCivixGroups(): List<CivixGroup> = readArray("groups.json", "groups") { it.toCivixGroup() }

    fun getCivixScrutins(): List<CivixScrutin> = readArray("scrutins.json", "scrutins") { it.toCivixScrutin() }

    fun getCivixVotes(): List<CivixVote> = readArray("votes.json", "votes") { it.toCivixVote() }

    fun getCivixDossiers(): List<CivixDossier> = readArray("dossiers.json", "dossiers") { it.toCivixDossier() }

    fun getCivixStats(): CivixStats = runCatching {
        val rootObject = readObject("stats.json")
        val stats = rootObject.optJSONObject("stats") ?: JSONObject()
        val counts = stats.optJSONObject("counts") ?: JSONObject()
        CivixStats(
            deputyCount = counts.optInt("deputies", 0),
            groupCount = counts.optInt("groups", 0),
            scrutinCount = counts.optInt("scrutins", 0),
            voteCount = counts.optInt("votes", 0),
            raw = stats
        )
    }.getOrElse { CivixStats() }

    fun getDeputyCivixProfile(deputyId: String): CivixDeputy? = getCivixDeputies().firstOrNull {
        it.id == deputyId || it.civixId == deputyId || it.uid == deputyId
    }

    fun getVotesForDeputy(deputyId: String): List<CivixVote> = getCivixVotes().filter {
        it.deputyId == deputyId || it.deputyCivixId == deputyId
    }

    fun getVotesForScrutin(scrutinId: String): List<CivixVote> = getCivixVotes().filter {
        it.scrutinId == scrutinId
    }

    fun getGroupStats(groupId: String): CivixGroupStats? {
        val group = getCivixGroups().firstOrNull { it.id == groupId || it.civixId == groupId || it.uid == groupId || it.abbreviation == groupId }
            ?: return null
        val deputies = getCivixDeputies().count { it.groupAbbreviation == group.abbreviation || it.groupUid == group.uid }
        val votes = getCivixVotes().filter { it.groupAbbreviation == group.abbreviation || it.groupId == group.id }
        return CivixGroupStats(group = group, deputyCount = deputies, voteCount = votes.size)
    }

    private fun readObject(fileName: String): JSONObject {
        val file = File(root, fileName)
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun <T> readArray(fileName: String, key: String, mapper: (JSONObject) -> T): List<T> {
        val array = readObject(fileName).optJSONArray(key) ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { runCatching { mapper(it) }.getOrNull() }
        }
    }
}

data class CivixDeputy(
    val id: String,
    val civixId: String?,
    val uid: String?,
    val displayName: String?,
    val groupUid: String?,
    val groupAbbreviation: String?,
    val raw: JSONObject
)

data class CivixGroup(
    val id: String,
    val civixId: String?,
    val uid: String?,
    val abbreviation: String?,
    val label: String?,
    val raw: JSONObject
)

data class CivixScrutin(
    val id: String,
    val civixId: String?,
    val uid: String?,
    val title: String?,
    val date: String?,
    val resultLabel: String?,
    val dossierRef: String?,
    val raw: JSONObject
)

data class CivixVote(
    val id: String,
    val scrutinId: String?,
    val deputyId: String?,
    val deputyCivixId: String?,
    val groupId: String?,
    val groupAbbreviation: String?,
    val position: String?,
    val deputyGroupAlignment: String?,
    val raw: JSONObject
)

data class CivixDossier(
    val id: String,
    val civixId: String?,
    val ref: String?,
    val title: String?,
    val currentStage: String?,
    val raw: JSONObject
)

data class CivixStats(
    val deputyCount: Int = 0,
    val groupCount: Int = 0,
    val scrutinCount: Int = 0,
    val voteCount: Int = 0,
    val raw: JSONObject = JSONObject()
)

data class CivixGroupStats(
    val group: CivixGroup,
    val deputyCount: Int,
    val voteCount: Int
)

private fun JSONObject.toCivixDeputy(): CivixDeputy = CivixDeputy(
    id = optString("id"),
    civixId = optNullableString("civixId"),
    uid = optNullableString("uid"),
    displayName = optNullableString("displayName"),
    groupUid = optNullableString("groupUid"),
    groupAbbreviation = optNullableString("groupAbbreviation"),
    raw = this
)

private fun JSONObject.toCivixGroup(): CivixGroup = CivixGroup(
    id = optString("id"),
    civixId = optNullableString("civixId"),
    uid = optNullableString("uid"),
    abbreviation = optNullableString("abbreviation"),
    label = optNullableString("label"),
    raw = this
)

private fun JSONObject.toCivixScrutin(): CivixScrutin = CivixScrutin(
    id = optString("id"),
    civixId = optNullableString("civixId"),
    uid = optNullableString("uid"),
    title = optNullableString("title"),
    date = optNullableString("date"),
    resultLabel = optNullableString("resultLabel"),
    dossierRef = optNullableString("dossierRef"),
    raw = this
)

private fun JSONObject.toCivixVote(): CivixVote = CivixVote(
    id = optString("id"),
    scrutinId = optNullableString("scrutinId"),
    deputyId = optNullableString("deputyId"),
    deputyCivixId = optNullableString("deputyCivixId"),
    groupId = optNullableString("groupId"),
    groupAbbreviation = optNullableString("groupAbbreviation"),
    position = optNullableString("position"),
    deputyGroupAlignment = optNullableString("deputyGroupAlignment"),
    raw = this
)

private fun JSONObject.toCivixDossier(): CivixDossier = CivixDossier(
    id = optString("id"),
    civixId = optNullableString("civixId"),
    ref = optNullableString("ref"),
    title = optNullableString("title"),
    currentStage = optNullableString("currentStage"),
    raw = this
)

private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
