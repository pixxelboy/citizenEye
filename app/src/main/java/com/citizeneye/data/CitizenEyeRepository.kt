package com.citizeneye.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

class CitizenEyeRepository(
    private val geoClient: GeoGouvClient = GeoGouvClient(),
    private val assembleeClient: AssembleeNationaleClient = AssembleeNationaleClient(),
    private val boundaryClient: ConstituencyBoundaryClient = ConstituencyBoundaryClient()
) {
    companion object {
        fun create(context: Context): CitizenEyeRepository {
            val cacheRoot = File(context.filesDir, "public-data")
            val publicDataCache = PublicDataCache(cacheRoot)
            return CitizenEyeRepository(
                geoClient = GeoGouvClient(),
                assembleeClient = AssembleeNationaleClient(publicDataCache = publicDataCache),
                boundaryClient = ConstituencyBoundaryClient(publicDataCache = publicDataCache)
            )
        }
    }
    suspend fun previewLocation(query: String): LocationPreview? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (!CitizenInputValidator.canSearch(cleanQuery)) return@withContext null
        val commune = geoClient.findCommune(cleanQuery) ?: return@withContext null
        val deputies = assembleeClient.fetchActiveDeputies()
            .filter { it.departmentCode == commune.departmentCode }
            .sortedBy { it.constituencyNumber.toIntOrNull() ?: 999 }
        LocationPreview(cleanQuery, commune, deputies)
    }

    suspend fun previewPreciseLocation(query: String, latitude: Double, longitude: Double): LocationPreview? = withContext(Dispatchers.IO) {
        val basePreview = previewLocation(query) ?: return@withContext null
        val boundary = runCatching { boundaryClient.resolve(latitude, longitude) }.getOrNull()
        if (boundary == null) {
            basePreview
        } else {
            val exactDeputies = basePreview.deputies.matching(boundary)
            if (exactDeputies.isEmpty()) basePreview.copy(preciseBoundary = boundary) else basePreview.copy(
                deputies = exactDeputies,
                preciseBoundary = boundary
            )
        }
    }

    suspend fun lookup(query: String): LookupState = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (!CitizenInputValidator.canSearch(cleanQuery)) {
            return@withContext LookupState.Error("Entrez un code postal à 5 chiffres ou au moins 2 lettres de ville.")
        }

        runCatching {
            val preview = previewLocation(cleanQuery)
                ?: return@withContext LookupState.Error("Aucune commune trouvée pour “$cleanQuery”.")
            val commune = preview.commune
            val deputies = preview.deputies

            if (deputies.isEmpty()) {
                return@withContext LookupState.Error("Aucun député actif trouvé pour le département ${commune.departmentCode ?: "inconnu"}.")
            }

            if (deputies.size == 1) {
                val depute = deputies.first()
                val votes = assembleeClient.fetchLegislatureVotesFor(depute.id)
                LookupState.Loaded(DeputeMatch(cleanQuery, commune, depute, votes))
            } else {
                LookupState.NeedSelection(
                    query = cleanQuery,
                    commune = commune,
                    deputies = deputies,
                    reason = "${commune.name} / ${commune.postalCodes.joinToString()} se trouve dans un département avec plusieurs circonscriptions. Choisissez votre député pour éviter une fausse certitude."
                )
            }
        }.getOrElse { error ->
            LookupState.Error("Impossible de charger les données publiques : ${error.message ?: error::class.java.simpleName}")
        }
    }

    suspend fun loadDeputyVotes(query: String, commune: Commune, depute: Depute): LookupState = withContext(Dispatchers.IO) {
        runCatching {
            LookupState.Loaded(DeputeMatch(query, commune, depute, assembleeClient.fetchLegislatureVotesFor(depute.id)))
        }.getOrElse { error ->
            LookupState.Error("Député sélectionné, mais les votes n’ont pas pu être chargés : ${error.message ?: error::class.java.simpleName}")
        }
    }
}

class GeoGouvClient {
    fun findCommune(query: String): Commune? {
        val url = if (CitizenInputValidator.isZipCode(query)) {
            "https://geo.api.gouv.fr/communes?codePostal=${encode(query)}&fields=nom,code,codesPostaux,codeDepartement&format=json&geometry=centre"
        } else {
            "https://geo.api.gouv.fr/communes?nom=${encode(query)}&fields=nom,code,codesPostaux,codeDepartement&format=json&geometry=centre&boost=population&limit=1"
        }
        val array = JSONArray(httpGet(url))
        if (array.length() == 0) return null
        val item = array.getJSONObject(0)
        val postals = item.optJSONArray("codesPostaux").toStringList()
        return Commune(
            name = item.optString("nom"),
            code = item.optString("code"),
            postalCodes = postals,
            departmentCode = item.optString("codeDepartement").ifBlank { item.optString("code").take(2) }
        )
    }
}

class AssembleeNationaleClient(
    private val publicDataCache: PublicDataCache? = null,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private var activeDeputiesCache: List<Depute>? = null
    private var activeDeputiesCacheMillis: Long = 0L

    fun fetchActiveDeputies(): List<Depute> {
        activeDeputiesCache?.takeIf { nowMillis() - activeDeputiesCacheMillis < PublicDataCache.ONE_DAY_MILLIS }?.let { return it }
        val organNames = mutableMapOf<String, String>()
        val deputies = mutableListOf<Depute>()

        readZipEntries(ACTIVE_DEPUTIES_URL, ACTIVE_DEPUTIES_CACHE_KEY) { name, bytes ->
            if (name.startsWith("json/organe/") && name.endsWith(".json")) {
                val organe = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("organe") ?: return@readZipEntries
                val uid = organe.optText("uid")
                val label = organe.optString("libelle", organe.optString("libelleAbrev"))
                if (uid.isNotBlank() && label.isNotBlank()) organNames[uid] = label
            }
        }

        readZipEntries(ACTIVE_DEPUTIES_URL, ACTIVE_DEPUTIES_CACHE_KEY) { name, bytes ->
            if (!name.startsWith("json/acteur/") || !name.endsWith(".json")) return@readZipEntries
            val actor = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("acteur") ?: return@readZipEntries
            val parliamentaryMandate = actor.mandates().firstOrNull { mandate ->
                mandate.optString("typeOrgane") == "ASSEMBLEE" && mandate.isActive()
            } ?: return@readZipEntries
            val place = parliamentaryMandate.optJSONObject("election")?.optJSONObject("lieu") ?: return@readZipEntries
            val ident = actor.optJSONObject("etatCivil")?.optJSONObject("ident") ?: return@readZipEntries
            val groupRef = actor.mandates().firstOrNull { it.optString("typeOrgane") == "GP" && it.isActive() }
                ?.optJSONObject("organes")?.optText("organeRef")
            val email = actor.optJSONObject("adresses")?.optJSONArrayOrObject("adresse")
                ?.firstNotNullOfOrNull { address ->
                    address.optString("valElec").takeIf { it.contains("@") }
                }

            deputies += Depute(
                id = actor.optText("uid"),
                name = "${ident.optString("prenom")} ${ident.optString("nom")}".trim(),
                group = groupRef?.let { organNames[it] } ?: "Groupe non renseigné",
                departmentName = place.optString("departement"),
                departmentCode = place.optString("numDepartement"),
                constituencyNumber = place.optString("numCirco"),
                email = email,
                photoUrl = deputyPhotoUrl(actor.optText("uid"))
            )
        }
        return deputies.sortedWith(compareBy<Depute> { it.departmentCode }.thenBy { it.constituencyNumber.toIntOrNull() ?: 999 }).also {
            activeDeputiesCache = it
            activeDeputiesCacheMillis = nowMillis()
        }
    }

    fun fetchLegislatureVotesFor(actorId: String): List<Vote> {
        val votes = mutableListOf<Vote>()
        readZipEntries(SCRUTINS_URL, SCRUTINS_CACHE_KEY) { name, bytes ->
            if (!name.endsWith(".json")) return@readZipEntries
            val scrutin = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("scrutin") ?: return@readZipEntries
            val position = findPosition(scrutin, actorId) ?: return@readZipEntries
            votes += Vote(
                id = scrutin.optString("uid"),
                number = scrutin.optString("numero"),
                date = scrutin.optString("dateScrutin"),
                title = scrutin.optString("titre").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                result = scrutin.optJSONObject("sort")?.optString("libelle") ?: scrutin.optJSONObject("syntheseVote")?.optString("annonce") ?: "Résultat non renseigné",
                summary = buildSummary(scrutin),
                deputePosition = position,
                sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/scrutins/${scrutin.optString("numero")}",
                voteBreakdown = buildVoteBreakdown(scrutin),
                groupPosition = findGroupPosition(scrutin, actorId, position),
                objectTitle = scrutin.optJSONObject("objet")?.optString("libelle"),
                dossierRef = scrutin.optJSONObject("objet")?.optJSONObject("dossierLegislatif")?.optString("dossierRef"),
                dossierTitle = scrutin.optJSONObject("objet")?.optJSONObject("dossierLegislatif")?.optString("libelle"),
                legislativeReference = scrutin.optJSONObject("objet")?.optString("referenceLegislative"),
                seanceRef = scrutin.optString("seanceRef")
            )
        }
        return votes.sortedByDescending { it.date }
    }

    private fun findPosition(scrutin: JSONObject, actorId: String): VotePosition? {
        val groupes = scrutin.optJSONObject("ventilationVotes")
            ?.optJSONObject("organe")
            ?.optJSONObject("groupes")
            ?.optJSONArrayOrObject("groupe") ?: return null
        for (group in groupes) {
            val nominatif = group.optJSONObject("vote")?.optJSONObject("decompteNominatif") ?: continue
            if (nominatif.containsActor("pours", actorId)) return VotePosition.POUR
            if (nominatif.containsActor("contres", actorId)) return VotePosition.CONTRE
            if (nominatif.containsActor("abstentions", actorId)) return VotePosition.ABSTENTION
            if (nominatif.containsActor("nonVotants", actorId) || nominatif.containsActor("nonVotantsVolontaires", actorId)) return VotePosition.NON_VOTANT
        }
        return null
    }

    private fun findGroupPosition(scrutin: JSONObject, actorId: String, deputyPosition: VotePosition): GroupVotePosition? {
        val groupes = scrutin.optJSONObject("ventilationVotes")
            ?.optJSONObject("organe")
            ?.optJSONObject("groupes")
            ?.optJSONArrayOrObject("groupe") ?: return null
        for (group in groupes) {
            val vote = group.optJSONObject("vote") ?: continue
            val nominatif = vote.optJSONObject("decompteNominatif") ?: continue
            val containsDeputy = nominatif.containsActor("pours", actorId) ||
                nominatif.containsActor("contres", actorId) ||
                nominatif.containsActor("abstentions", actorId) ||
                nominatif.containsActor("nonVotants", actorId) ||
                nominatif.containsActor("nonVotantsVolontaires", actorId)
            if (!containsDeputy) continue
            val decompte = vote.optJSONObject("decompteVoix")
            val forCount = decompte?.optNullableInt("pour")
            val againstCount = decompte?.optNullableInt("contre")
            val abstentionCount = decompte?.optNullableInt("abstentions")
            val nonVotingCount = decompte?.optNullableInt("nonVotants")
            val majority = listOf(
                VotePosition.POUR to forCount,
                VotePosition.CONTRE to againstCount,
                VotePosition.ABSTENTION to abstentionCount,
                VotePosition.NON_VOTANT to nonVotingCount
            ).filter { it.second != null }.maxByOrNull { it.second ?: -1 }?.first
            return GroupVotePosition(
                groupName = group.optString("libelle", group.optString("organeRef", "Groupe parlementaire")),
                groupMajorityPosition = majority,
                deputyVotedLikeGroup = majority?.let { it == deputyPosition },
                forCount = forCount,
                againstCount = againstCount,
                abstentionCount = abstentionCount,
                nonVotingCount = nonVotingCount
            )
        }
        return null
    }

    private fun buildVoteBreakdown(scrutin: JSONObject): VoteBreakdown? {
        val synthese = scrutin.optJSONObject("syntheseVote") ?: return null
        val decompte = synthese.optJSONObject("decompte")
        return VoteBreakdown(
            totalVoters = synthese.optNullableInt("nombreVotants"),
            forCount = decompte?.optNullableInt("pour"),
            againstCount = decompte?.optNullableInt("contre"),
            abstentionCount = decompte?.optNullableInt("abstentions"),
            nonVotingCount = synthese.optNullableInt("nombreNonVotants"),
            absoluteMajority = synthese.optNullableInt("majoriteAbsolue"),
            resultLabel = scrutin.optJSONObject("sort")?.optString("libelle") ?: synthese.optString("annonce").ifBlank { null }
        )
    }

    private fun buildSummary(scrutin: JSONObject): String {
        val synthese = scrutin.optJSONObject("syntheseVote")
        val decompte = synthese?.optJSONObject("decompte")
        return if (decompte != null) {
            "Pour ${decompte.optString("pour", "0")}, contre ${decompte.optString("contre", "0")}, abstentions ${decompte.optString("abstentions", "0")}. Source : Assemblée nationale, scrutin n°${scrutin.optString("numero")}."
        } else {
            "Source : Assemblée nationale, scrutin n°${scrutin.optString("numero")} du ${scrutin.optString("dateScrutin")}."
        }
    }

    companion object {
        private const val ACTIVE_DEPUTIES_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip"
        private const val SCRUTINS_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip"
        private const val ACTIVE_DEPUTIES_CACHE_KEY = "assemblee-active-deputies-v17.zip"
        private const val SCRUTINS_CACHE_KEY = "assemblee-scrutins-v17.zip"
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
}

private fun httpGet(url: String): String {
    return httpGetBytes(url).toString(Charsets.UTF_8)
}

internal fun httpGetBytes(url: String): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 120_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "CitizenEye Android MVP")
    }
    return connection.inputStream.use { it.readBytes() }
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun JSONObject.optText(key: String): String {
    val value = opt(key)
    return when (value) {
        is JSONObject -> value.optString("#text")
        null -> ""
        JSONObject.NULL -> ""
        else -> value.toString()
    }
}

private fun JSONObject.isActive(): Boolean = isNull("dateFin") || optString("dateFin").isBlank()

private fun JSONObject.optNullableInt(key: String): Int? {
    val raw = opt(key)
    return when (raw) {
        null, JSONObject.NULL -> null
        is Number -> raw.toInt()
        else -> raw.toString().trim().toIntOrNull()
    }
}

private fun JSONObject.mandates(): List<JSONObject> = optJSONObject("mandats")
    ?.optJSONArrayOrObject("mandat")
    .orEmpty()

private fun JSONObject.optJSONArrayOrObject(key: String): List<JSONObject> {
    val raw = opt(key)
    return when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
        is JSONObject -> listOf(raw)
        else -> emptyList()
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

private fun JSONObject.containsActor(category: String, actorId: String): Boolean {
    val bucket = optJSONObject(category) ?: return false
    return bucket.optJSONArrayOrObject("votant").any { it.optString("acteurRef") == actorId }
}
