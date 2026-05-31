package com.citizeneye.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.GZIPInputStream

open class StaticCitizenEyeDatasetClient(
    private val cacheRoot: File,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val downloader: (String) -> ByteArray = ::httpGetBytes
) {
    private var datasetCache: Map<String, ByteArray>? = null
    private var datasetCacheMillis: Long = 0L
    private var parsedDeputiesCache: List<Depute>? = null
    private var parsedVotesByDeputyCache: Map<String, List<Vote>>? = null

    open fun fetchActiveDeputies(): List<Depute> {
        val files = ensureDataset()
        parsedDeputiesCache?.let { return it }
        val json = JSONObject(gunzip(files.getValue("deputies")).toString(Charsets.UTF_8))
        return json.optJSONArray("deputies").orEmptyObjects().map { it.toDepute() }.also { parsedDeputiesCache = it }
    }

    open fun fetchLegislatureVotesFor(actorId: String): List<Vote> = fetchAllLegislatureVotesByDeputy()[actorId].orEmpty()

    open fun fetchAllLegislatureVotesByDeputy(): Map<String, List<Vote>> {
        val files = ensureDataset()
        parsedVotesByDeputyCache?.let { return it }
        val root = JSONObject(gunzip(files.getValue("votes")).toString(Charsets.UTF_8))
        val votesByDeputy = root.optJSONObject("votesByDeputy") ?: return emptyMap<String, List<Vote>>().also { parsedVotesByDeputyCache = it }
        return votesByDeputy.keys().asSequence().associateWith { actorId ->
            votesByDeputy.optJSONArray(actorId).orEmptyObjects().map { it.toVote() }.sortedByDescending { it.date }
        }.also { parsedVotesByDeputyCache = it }
    }

    open fun findParentText(dossierRef: String): ParentTextDetails? {
        if (dossierRef.isBlank()) return null
        val files = ensureDataset()
        val root = JSONObject(gunzip(files.getValue("dossiers")).toString(Charsets.UTF_8))
        val dossier = root.optJSONObject("dossiersByRef")?.optJSONObject(dossierRef) ?: return null
        return dossier.toParentTextDetails(dossierRef)
    }

    open fun fetchUpcomingVotes(limit: Int = 30): List<UpcomingVote> {
        val files = ensureDataset()
        val root = JSONObject(gunzip(files.getValue("dossiers")).toString(Charsets.UTF_8))
        val dossiers = root.optJSONObject("dossiersByRef") ?: return emptyList()
        return dossiers.keys().asSequence()
            .mapNotNull { ref -> dossiers.optJSONObject(ref)?.toUpcomingVote(ref) }
            .filter { vote -> vote.title.isNotBlank() }
            .sortedWith(compareBy<UpcomingVote> { it.status.ordinal }.thenBy { it.title })
            .take(limit)
            .toList()
    }

    open fun fetchParliamentCalendar(limit: Int = 50): List<UpcomingVote> = runCatching {
        val root = loadCachedStaticJson(PARLIAMENT_CALENDAR_FILE)
        val items = root.optJSONArray("items").orEmptyObjects()
        val today = LocalDate.now().toString()
        items.asSequence()
            .mapNotNull { it.toCalendarUpcomingVote() }
            .filter { it.eventDate != null && it.eventDate >= today && (it.sourceUrls.isNotEmpty() || it.sourceUrl.isNotBlank()) }
            .sortedWith(compareBy<UpcomingVote> { it.eventDateTime ?: it.eventDate ?: "" }.thenBy { it.title })
            .take(limit)
            .toList()
    }.getOrElse { emptyList() }

    private fun loadCachedStaticJson(path: String): JSONObject {
        cacheRoot.mkdirs()
        val file = File(cacheRoot, path)
        val now = nowMillis()
        if (file.exists() && now - file.lastModified() in 0 until PublicDataCache.ONE_DAY_MILLIS) {
            return JSONObject(file.readText())
        }
        return runCatching {
            val bytes = downloader(resolveUrl(path))
            atomicWrite(file, bytes)
            file.setLastModified(now)
            JSONObject(bytes.toString(Charsets.UTF_8))
        }.getOrElse { error ->
            if (file.exists()) JSONObject(file.readText()) else throw error
        }
    }

    private fun ensureDataset(): Map<String, ByteArray> {
        cacheRoot.mkdirs()
        val now = nowMillis()
        datasetCache?.takeIf { now - datasetCacheMillis in 0 until PublicDataCache.ONE_DAY_MILLIS }?.let { return it }

        val localManifest = File(cacheRoot, MANIFEST_FILE)
        loadFreshLocalDataset(localManifest, now)?.let { return it }

        return runCatching {
            val remoteManifestBytes = downloader(resolveUrl(MANIFEST_FILE))
            val remoteManifest = JSONObject(remoteManifestBytes.toString(Charsets.UTF_8))
            if (localManifest.exists()) {
                val cachedManifest = JSONObject(localManifest.readText())
                if (cachedManifest.optString("version") == remoteManifest.optString("version") && allManifestFilesExist(cachedManifest)) {
                    return cacheDataset(loadFilesFromManifest(cachedManifest), now)
                }
            }
            downloadFiles(remoteManifest).also {
                atomicWrite(localManifest, remoteManifestBytes)
                localManifest.setLastModified(now)
                cacheDataset(it, now)
            }
        }.getOrElse { error ->
            if (localManifest.exists() && allManifestFilesExist(JSONObject(localManifest.readText()))) {
                cacheDataset(loadFilesFromManifest(JSONObject(localManifest.readText())), now)
            } else {
                throw error
            }
        }
    }

    private fun loadFreshLocalDataset(localManifest: File, now: Long): Map<String, ByteArray>? {
        if (!localManifest.exists()) return null
        val manifestAgeMillis = now - localManifest.lastModified()
        if (manifestAgeMillis !in 0 until PublicDataCache.ONE_DAY_MILLIS) return null
        val manifest = JSONObject(localManifest.readText())
        if (!allManifestFilesExist(manifest)) return null
        return cacheDataset(loadFilesFromManifest(manifest), now)
    }

    private fun cacheDataset(files: Map<String, ByteArray>, now: Long): Map<String, ByteArray> {
        if (datasetCache !== files) {
            parsedDeputiesCache = null
            parsedVotesByDeputyCache = null
        }
        datasetCache = files
        datasetCacheMillis = now
        return files
    }

    private fun downloadFiles(manifest: JSONObject): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        manifestFiles(manifest).forEach { file ->
            val bytes = downloader(resolveUrl(file.url))
            check(sha256(bytes) == file.sha256) { "Static dataset SHA mismatch for ${file.name}" }
            check(bytes.size == file.bytes) { "Static dataset byte length mismatch for ${file.name}" }
            atomicWrite(File(cacheRoot, file.url), bytes)
            files[file.name] = bytes
        }
        return files
    }

    private fun loadFilesFromManifest(manifest: JSONObject): Map<String, ByteArray> = manifestFiles(manifest).associate { file ->
        val bytes = File(cacheRoot, file.url).readBytes()
        check(sha256(bytes) == file.sha256) { "Cached static dataset SHA mismatch for ${file.name}" }
        file.name to bytes
    }

    private fun allManifestFilesExist(manifest: JSONObject): Boolean = manifestFiles(manifest).all { File(cacheRoot, it.url).exists() }

    private fun manifestFiles(manifest: JSONObject): List<ManifestFile> = manifest.optJSONArray("files").orEmptyObjects().mapNotNull { item ->
        val name = item.optString("name")
        val url = item.optString("url")
        val sha = item.optString("sha256")
        val bytes = item.optLong("bytes", -1L)
        if (name.isBlank() || url.isBlank() || sha.isBlank() || bytes < 0) null else ManifestFile(name, url, sha, bytes.toInt())
    }

    private fun resolveUrl(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private data class ManifestFile(val name: String, val url: String, val sha256: String, val bytes: Int)

    companion object {
        const val DEFAULT_BASE_URL = "https://pixxelboy.github.io/citizenEye/"
        private const val MANIFEST_FILE = "manifest.json"
        private const val PARLIAMENT_CALENDAR_FILE = "data/parliament/calendar.json"
    }
}

private fun JSONObject.toDepute(): Depute = Depute(
    id = optString("id"),
    name = optString("name"),
    group = optString("group").ifBlank { optString("politicalGroupAbbreviation", "N/R") },
    departmentName = optString("departmentName"),
    departmentCode = optString("departmentCode"),
    constituencyNumber = optString("constituencyNumber"),
    email = optNullableString("email"),
    regionName = optNullableString("regionName"),
    profession = optNullableString("profession"),
    politicalGroupFullName = optString("politicalGroupFullName", "Groupe non renseigné"),
    politicalGroupAbbreviation = optString("politicalGroupAbbreviation", optString("group", "N/R")),
    photoUrl = deputyPhotoUrl(optString("id")) ?: optNullableString("photoUrl")
)

private fun JSONObject.toVote(): Vote = Vote(
    id = optString("id"),
    number = optString("number"),
    date = optString("date"),
    title = optString("title"),
    result = optString("result"),
    summary = optString("summary"),
    deputePosition = VotePosition.valueOf(optString("deputePosition", VotePosition.NON_VOTANT.name)),
    sourceUrl = optString("sourceUrl"),
    voteBreakdown = optJSONObject("voteBreakdown")?.toVoteBreakdown(),
    groupPosition = optJSONObject("groupPosition")?.toGroupVotePosition(),
    objectTitle = optNullableString("objectTitle"),
    dossierRef = optNullableString("dossierRef"),
    dossierTitle = optNullableString("dossierTitle"),
    legislativeReference = optNullableString("legislativeReference"),
    seanceRef = optNullableString("seanceRef")
)

private fun JSONObject.toVoteBreakdown(): VoteBreakdown = VoteBreakdown(
    totalVoters = optNullableIntLocal("totalVoters"),
    forCount = optNullableIntLocal("forCount"),
    againstCount = optNullableIntLocal("againstCount"),
    abstentionCount = optNullableIntLocal("abstentionCount"),
    nonVotingCount = optNullableIntLocal("nonVotingCount"),
    absoluteMajority = optNullableIntLocal("absoluteMajority"),
    resultLabel = optNullableString("resultLabel")
)

private fun JSONObject.toGroupVotePosition(): GroupVotePosition = GroupVotePosition(
    groupName = optString("groupName", "Groupe parlementaire"),
    groupMajorityPosition = optNullableString("groupMajorityPosition")?.let { VotePosition.valueOf(it) },
    deputyVotedLikeGroup = if (isNull("deputyVotedLikeGroup")) null else optBoolean("deputyVotedLikeGroup"),
    forCount = optNullableIntLocal("forCount"),
    againstCount = optNullableIntLocal("againstCount"),
    abstentionCount = optNullableIntLocal("abstentionCount"),
    nonVotingCount = optNullableIntLocal("nonVotingCount")
)

private fun JSONObject.toParentTextDetails(ref: String): ParentTextDetails = ParentTextDetails(
    title = optNullableString("title"),
    type = optNullableString("type"),
    procedureStage = optNullableString("procedureStage"),
    legislature = optNullableString("legislature"),
    dossierUrl = officialDossierUrl(ref) ?: optNullableString("dossierUrl"),
    commissionName = optNullableString("commissionName"),
    rapporteurs = optJSONArray("rapporteurs").orEmptyStrings(),
    depositNumber = optNullableString("depositNumber"),
    adoptionStatus = optNullableString("adoptionStatus")
)

private fun JSONObject.toCalendarUpcomingVote(): UpcomingVote? {
    val id = optNullableString("id") ?: return null
    val title = optNullableString("title") ?: optNullableString("shortTitle") ?: return null
    val eventDate = optNullableString("eventDate") ?: return null
    val sourceUrls = optJSONArray("sourceUrls").orEmptyStrings().filter { it.startsWith("https://") }
    val officialUrl = optNullableString("officialUrl")?.takeIf { it.startsWith("https://") }
    if (sourceUrls.isEmpty() && officialUrl == null) return null
    val eventType = optNullableString("eventType")
    val status = when (eventType) {
        "vote" -> UpcomingVoteStatus.SCHEDULED_VOTE
        "committee", "hearing" -> UpcomingVoteStatus.COMMITTEE_REVIEW
        "debate", "public_session" -> UpcomingVoteStatus.UNDER_DISCUSSION
        else -> UpcomingVoteStatus.AGENDA_ITEM
    }
    val sourceUrl = officialUrl ?: sourceUrls.first()
    val stage = optNullableString("stage") ?: status.label
    val dateLabel = optNullableString("dateLabel") ?: eventDate
    return UpcomingVote(
        id = id,
        title = title,
        shortSummary = optNullableString("shortTitle") ?: citizenSummaryFor(title),
        citizenSummary = citizenSummaryFor(title),
        currentStage = stage,
        status = status,
        expectedDateLabel = dateLabel,
        sourceUrl = sourceUrl,
        officialDocuments = (listOf(sourceUrl) + sourceUrls).distinct(),
        timeline = listOf(UpcomingVoteTimelineEvent(stage, dateLabel)),
        eventDate = eventDate,
        eventDateTime = optNullableString("eventDateTime"),
        chamber = optNullableString("chamber"),
        eventType = eventType,
        dateConfidence = optNullableString("confidence"),
        sourceUrls = sourceUrls
    )
}

private fun JSONObject.toUpcomingVote(ref: String): UpcomingVote? {
    val title = optNullableString("title") ?: return null
    val adoptionStatus = optNullableString("adoptionStatus")
    if (adoptionStatus?.contains("adopt", ignoreCase = true) == true || adoptionStatus?.contains("rejet", ignoreCase = true) == true) {
        return null
    }
    val stage = optNullableString("procedureStage") ?: optNullableString("type") ?: "Étape parlementaire en cours"
    val status = when {
        stage.contains("commission", ignoreCase = true) || optNullableString("commissionName") != null -> UpcomingVoteStatus.COMMITTEE_REVIEW
        stage.contains("séance", ignoreCase = true) || stage.contains("discussion", ignoreCase = true) || stage.contains("debat", ignoreCase = true) || stage.contains("débat", ignoreCase = true) -> UpcomingVoteStatus.UNDER_DISCUSSION
        else -> UpcomingVoteStatus.AGENDA_ITEM
    }
    val sourceUrl = officialDossierUrl(ref) ?: optNullableString("dossierUrl") ?: "https://www.assemblee-nationale.fr/dyn/17/dossiers/$ref"
    return UpcomingVote(
        id = ref,
        title = title,
        shortSummary = citizenSummaryFor(title),
        citizenSummary = citizenSummaryFor(title),
        currentStage = stage,
        status = status,
        expectedDateLabel = "Date non annoncée",
        sourceUrl = sourceUrl,
        officialDocuments = listOf(sourceUrl),
        timeline = listOfNotNull(
            optNullableString("depositNumber")?.let { UpcomingVoteTimelineEvent("Texte déposé n°$it") },
            UpcomingVoteTimelineEvent(stage)
        )
    )
}

private fun citizenSummaryFor(title: String): String {
    val cleaned = title.replace(Regex("\\s+"), " ").trim()
    return when {
        cleaned.length <= 150 -> "Texte parlementaire en cours : $cleaned. CitizenEye affiche uniquement les informations officielles disponibles."
        else -> "Texte parlementaire en cours : ${cleaned.take(147).trimEnd()}… CitizenEye affiche uniquement les informations officielles disponibles."
    }
}

private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

private fun JSONArray?.orEmptyStrings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}

private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun officialDossierUrl(ref: String): String? = ref
    .takeIf { it.isNotBlank() }
    ?.let { "https://www.assemblee-nationale.fr/dyn/17/dossiers/$it" }

private fun JSONObject.optNullableIntLocal(key: String): Int? {
    val raw = opt(key)
    return when (raw) {
        null, JSONObject.NULL -> null
        is Number -> raw.toInt()
        else -> raw.toString().trim().toIntOrNull()
    }
}

private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

private fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeBytes(bytes)
    if (!temp.renameTo(file)) {
        file.writeBytes(bytes)
        temp.delete()
    }
}
