package com.citizeneye.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

interface OfficialVoteEnrichmentRepository {
    suspend fun findParentTextForVote(vote: Vote): ParentTextDetails?
    suspend fun findAmendmentDetailsForVote(vote: Vote): AmendmentDetails?
    suspend fun findArticleDetailsForVote(vote: Vote): ArticleDetails?
    suspend fun findMotionDetailsForVote(vote: Vote): MotionDetails?
}

object EmptyOfficialVoteEnrichmentRepository : OfficialVoteEnrichmentRepository {
    override suspend fun findParentTextForVote(vote: Vote): ParentTextDetails? = null
    override suspend fun findAmendmentDetailsForVote(vote: Vote): AmendmentDetails? = null
    override suspend fun findArticleDetailsForVote(vote: Vote): ArticleDetails? = null
    override suspend fun findMotionDetailsForVote(vote: Vote): MotionDetails? = null
}

class AssembleeOfficialVoteEnrichmentRepository(
    private val publicDataCache: PublicDataCache? = null
) : OfficialVoteEnrichmentRepository {
    private var dossierCache: MutableMap<String, ParentTextDetails?>? = null

    override suspend fun findParentTextForVote(vote: Vote): ParentTextDetails? = withContext(Dispatchers.IO) {
        val dossierRef = vote.dossierRef?.takeIf { it.isNotBlank() } ?: return@withContext vote.dossierTitle?.let {
            ParentTextDetails(
                title = it,
                type = inferParentTextType(it),
                procedureStage = inferProcedureStage(vote.title),
                legislature = "17",
                dossierUrl = null,
                commissionName = null,
                rapporteurs = emptyList(),
                depositNumber = null,
                adoptionStatus = null
            )
        }
        dossierCache ?: loadDossierCache().also { dossierCache = it }
        dossierCache?.get(dossierRef) ?: vote.dossierTitle?.let {
            ParentTextDetails(
                title = it,
                type = inferParentTextType(it),
                procedureStage = inferProcedureStage(vote.title),
                legislature = "17",
                dossierUrl = null,
                commissionName = null,
                rapporteurs = emptyList(),
                depositNumber = null,
                adoptionStatus = null
            )
        }
    }

    override suspend fun findAmendmentDetailsForVote(vote: Vote): AmendmentDetails? {
        if (classifyVoteSubjectType(vote.title) != VoteSubjectType.AMENDMENT) return null
        val number = Regex("(?i)amendement(?:s)?\\s+(?:n[°oº]\\s*)?([A-Za-z0-9._-]+)").find(vote.title)?.groupValues?.getOrNull(1)
        val authors = Regex("(?i)amendement(?:s)?[^ ]*.*?\\s+de\\s+(.+?)(?:\\s+à l['’]article|\\s+après l['’]article|\\s+avant l['’]article|\\s+du projet|\\s+de la proposition|\\s+et l['’]amendement|\\s*\\()")
            .find(vote.title)?.groupValues?.getOrNull(1)?.split(" et ", ",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        return AmendmentDetails(
            number = number,
            authors = authors,
            group = null,
            status = vote.result,
            objectText = vote.objectTitle ?: vote.title,
            proposedChangeText = null,
            explanatoryStatement = null,
            sourceUrl = vote.sourceUrl
        )
    }

    override suspend fun findArticleDetailsForVote(vote: Vote): ArticleDetails? {
        if (classifyVoteSubjectType(vote.title) != VoteSubjectType.ARTICLE) return null
        val number = Regex("(?i)article\\s+([A-Za-z0-9._-]+|premier)").find(vote.title)?.groupValues?.getOrNull(1)
        return ArticleDetails(
            number = number,
            title = number?.let { "Article $it" },
            summary = vote.objectTitle ?: vote.title,
            sourceUrl = vote.sourceUrl
        )
    }

    override suspend fun findMotionDetailsForVote(vote: Vote): MotionDetails? {
        val subject = classifyVoteSubjectType(vote.title)
        if (subject != VoteSubjectType.NO_CONFIDENCE_MOTION && subject != VoteSubjectType.PROCEDURAL_MOTION) return null
        return MotionDetails(
            type = when (subject) {
                VoteSubjectType.NO_CONFIDENCE_MOTION -> "Motion de censure"
                else -> "Motion de procédure"
            },
            authors = emptyList(),
            explanation = vote.objectTitle ?: vote.title,
            politicalEffectExplanation = buildSubjectExplanation(subject),
            sourceUrl = vote.sourceUrl
        )
    }

    private fun loadDossierCache(): MutableMap<String, ParentTextDetails?> {
        val result = mutableMapOf<String, ParentTextDetails?>()
        readZipEntries(DOSSIERS_URL, DOSSIERS_CACHE_KEY) { name, bytes ->
            if (!name.startsWith("json/dossierParlementaire/") || !name.endsWith(".json")) return@readZipEntries
            val dossier = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("dossierParlementaire") ?: return@readZipEntries
            val uid = dossier.optString("uid")
            if (uid.isNotBlank()) result[uid] = parseParentTextDetails(dossier)
        }
        return result
    }

    private fun readZipEntries(url: String, cacheKey: String, onEntry: (String, ByteArray) -> Unit) {
        val zipBytes = publicDataCache?.getBytes(url, cacheKey) ?: httpGetBytes(url)
        ZipInputStream(BufferedInputStream(zipBytes.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) onEntry(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }

    companion object {
        private const val DOSSIERS_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/dossiers_legislatifs/Dossiers_Legislatifs.json.zip"
        private const val DOSSIERS_CACHE_KEY = "assemblee-dossiers-legislatifs-v17.zip"
    }
}

internal fun parseParentTextDetails(dossier: JSONObject): ParentTextDetails {
    val title = dossier.optJSONObject("titreDossier")?.optString("titre")
    val procedure = dossier.optJSONObject("procedureParlementaire")?.optString("libelle")
    val acts = dossier.optJSONObject("actesLegislatifs")?.optJSONArrayOrObjectLocal("acteLegislatif").orEmpty().flatMap { flattenActs(it) }
    val latestStage = acts.lastOrNull { it.optJSONObject("libelleActe")?.optString("libelleCourt").orEmpty().isNotBlank() }
    val commissionRef = acts.firstOrNull { it.optString("codeActe").contains("COM-FOND") }?.optString("organeRef")
    val depositTextRef = acts.firstNotNullOfOrNull { it.optString("texteAssocie").takeIf { ref -> ref.isNotBlank() } }
    val depositNumber = Regex("B(\\d+)").find(depositTextRef.orEmpty())?.groupValues?.getOrNull(1)
    val adoptionStatus = acts.lastOrNull { it.optString("codeActe").contains("ADOPTION", ignoreCase = true) || it.optString("codeActe").contains("REJET", ignoreCase = true) }
        ?.optJSONObject("libelleActe")?.optString("libelleCourt")
    val rapporteurs = acts.flatMap { act ->
        act.optJSONObject("rapporteurs")?.optJSONArrayOrObjectLocal("rapporteur")?.mapNotNull { it.optString("acteurRef").takeIf(String::isNotBlank) }.orEmpty()
    }.distinct()
    return ParentTextDetails(
        title = title,
        type = procedure ?: title?.let { inferParentTextType(it) },
        procedureStage = latestStage?.optJSONObject("libelleActe")?.optString("libelleCourt") ?: latestStage?.optJSONObject("libelleActe")?.optString("nomCanonique"),
        legislature = dossier.optString("legislature").ifBlank { null },
        dossierUrl = dossierUrl(dossier.optString("uid"), title),
        commissionName = commissionRef,
        rapporteurs = rapporteurs,
        depositNumber = depositNumber,
        adoptionStatus = adoptionStatus
    )
}

private fun flattenActs(act: JSONObject): List<JSONObject> {
    val children = act.optJSONObject("actesLegislatifs")?.optJSONArrayOrObjectLocal("acteLegislatif").orEmpty().flatMap { flattenActs(it) }
    return listOf(act) + children
}

internal fun inferParentTextType(title: String): String = when {
    title.contains("projet de loi de finances", ignoreCase = true) -> "Projet de loi de finances"
    title.contains("financement de la sécurité sociale", ignoreCase = true) || title.contains("financement de la securite sociale", ignoreCase = true) -> "Projet de loi de financement de la sécurité sociale"
    title.contains("projet de loi constitutionnelle", ignoreCase = true) -> "Révision constitutionnelle"
    title.contains("projet de loi", ignoreCase = true) -> "Projet de loi"
    title.contains("proposition de loi", ignoreCase = true) -> "Proposition de loi"
    title.contains("résolution", ignoreCase = true) || title.contains("resolution", ignoreCase = true) -> "Proposition de résolution"
    title.contains("accord", ignoreCase = true) || title.contains("convention", ignoreCase = true) || title.contains("ratification", ignoreCase = true) -> "Accord international"
    else -> "Dossier législatif"
}

private fun inferProcedureStage(title: String): String? = Regex("\\(([^)]*lecture[^)]*)\\)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.getOrNull(1)

private fun dossierUrl(dossierRef: String, title: String?): String? {
    if (title.isNullOrBlank() && dossierRef.isBlank()) return null
    val query = java.net.URLEncoder.encode(title ?: dossierRef, "UTF-8")
    return "https://www.assemblee-nationale.fr/dyn/recherche?search=$query"
}

private fun JSONObject.optJSONArrayOrObjectLocal(key: String): List<JSONObject> {
    val raw = opt(key)
    return when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
        is JSONObject -> listOf(raw)
        else -> emptyList()
    }
}
