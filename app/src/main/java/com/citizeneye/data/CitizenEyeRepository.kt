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
            val staticDatasetClient = StaticCitizenEyeDatasetClient(File(context.filesDir, "static-public-data"))
            return CitizenEyeRepository(
                geoClient = GeoGouvClient(),
                assembleeClient = AssembleeNationaleClient(publicDataCache = publicDataCache, staticDatasetClient = staticDatasetClient),
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
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val staticDatasetClient: StaticCitizenEyeDatasetClient? = null
) {
    private var activeDeputiesCache: List<Depute>? = null
    private var activeDeputiesCacheMillis: Long = 0L

    fun fetchActiveDeputies(): List<Depute> {
        activeDeputiesCache?.takeIf { nowMillis() - activeDeputiesCacheMillis < PublicDataCache.ONE_DAY_MILLIS }?.let { return it }
        staticDatasetClient?.let { client ->
            runCatching { client.fetchActiveDeputies() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { staticDeputies ->
                return staticDeputies.sortedWith(compareBy<Depute> { it.departmentCode }.thenBy { it.constituencyNumber.toIntOrNull() ?: 999 }).also {
                    activeDeputiesCache = it
                    activeDeputiesCacheMillis = nowMillis()
                }
            }
        }
        val politicalGroups = mutableMapOf<String, PoliticalGroupMetadata>()
        val deputies = mutableListOf<Depute>()

        readZipEntries(ACTIVE_DEPUTIES_URL, ACTIVE_DEPUTIES_CACHE_KEY) { name, bytes ->
            if (name.startsWith("json/organe/") && name.endsWith(".json")) {
                val organe = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("organe") ?: return@readZipEntries
                val metadata = PoliticalGroupMetadata.fromOrgane(organe) ?: return@readZipEntries
                politicalGroups[metadata.uid] = metadata
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
            val politicalGroup = groupRef?.let { politicalGroups[it] } ?: PoliticalGroupMetadata.missing()
            val departmentCode = place.optString("numDepartement")

            deputies += Depute(
                id = actor.optText("uid"),
                name = "${ident.optString("prenom")} ${ident.optString("nom")}".trim(),
                group = politicalGroup.abbreviation,
                departmentName = place.optString("departement"),
                departmentCode = departmentCode,
                constituencyNumber = place.optString("numCirco"),
                email = email,
                regionName = parseDeputyRegion(place),
                profession = parseDeputyProfession(actor.optJSONObject("profession")),
                politicalGroupFullName = politicalGroup.fullName,
                politicalGroupAbbreviation = politicalGroup.abbreviation,
                photoUrl = deputyPhotoUrl(actor.optText("uid"))
            )
        }
        return deputies.sortedWith(compareBy<Depute> { it.departmentCode }.thenBy { it.constituencyNumber.toIntOrNull() ?: 999 }).also {
            activeDeputiesCache = it
            activeDeputiesCacheMillis = nowMillis()
        }
    }

    fun fetchLegislatureVotesFor(actorId: String): List<Vote> {
        staticDatasetClient?.let { client ->
            runCatching { client.fetchLegislatureVotesFor(actorId) }.getOrNull()?.let { return it }
        }
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

internal data class PoliticalGroupMetadata(
    val uid: String,
    val fullName: String,
    val abbreviation: String
) {
    companion object {
        fun missing(): PoliticalGroupMetadata = PoliticalGroupMetadata(
            uid = "",
            fullName = "Groupe non renseigné",
            abbreviation = "N/R"
        )

        fun fromOrgane(organe: JSONObject): PoliticalGroupMetadata? {
            val codeType = organe.optString("codeType")
            val typeOrgane = organe.optString("typeOrgane")
            if (codeType != "GP" && typeOrgane != "GP") return null
            val uid = organe.optText("uid").takeIf { it.isNotBlank() } ?: return null
            val rawFullName = organe.optCleanString("libelle")
            val rawAbbreviation = organe.optCleanString("libelleAbrev") ?: organe.optCleanString("libelleAbrege")
            val fullName = rawFullName ?: rawAbbreviation ?: "Groupe non renseigné"
            val abbreviation = rawAbbreviation ?: rawFullName ?: "N/R"
            return PoliticalGroupMetadata(uid = uid, fullName = fullName, abbreviation = abbreviation)
        }
    }
}

internal fun parseDeputyProfession(profession: JSONObject?): String? {
    if (profession == null) return null
    profession.optCleanString("libelleCourant")?.let { return cleanProfessionLabel(it) }
    val insee = profession.optJSONObject("socProcINSEE")
    return insee?.optCleanString("catSocPro")
        ?: insee?.optCleanString("famSocPro")
}

private fun cleanProfessionLabel(value: String): String = value
    .replace(Regex("^\\(\\d+\\)\\s*-\\s*"), "")
    .trim()

internal fun parseDeputyRegion(place: JSONObject?): String? {
    if (place == null) return null
    place.optCleanString("region")?.let { return it }
    return regionNameForDepartmentCode(place.optString("numDepartement"))
}

internal fun regionNameForDepartmentCode(rawDepartmentCode: String?): String? {
    val code = rawDepartmentCode?.trim()?.uppercase()?.removePrefix("0")?.ifBlank { null } ?: return null
    return DEPARTMENT_CODE_TO_REGION[code]
        ?: DEPARTMENT_CODE_TO_REGION[code.padStart(2, '0')]
        ?: when {
            code == "99" || code == "099" -> "Français établis hors de France"
            code.startsWith("99") -> "Français établis hors de France"
            else -> null
        }
}

private val DEPARTMENT_CODE_TO_REGION: Map<String, String> = mapOf(
    "01" to "Auvergne-Rhône-Alpes", "1" to "Auvergne-Rhône-Alpes",
    "02" to "Hauts-de-France", "2" to "Hauts-de-France",
    "03" to "Auvergne-Rhône-Alpes", "3" to "Auvergne-Rhône-Alpes",
    "04" to "Provence-Alpes-Côte d’Azur", "4" to "Provence-Alpes-Côte d’Azur",
    "05" to "Provence-Alpes-Côte d’Azur", "5" to "Provence-Alpes-Côte d’Azur",
    "06" to "Provence-Alpes-Côte d’Azur", "6" to "Provence-Alpes-Côte d’Azur",
    "07" to "Auvergne-Rhône-Alpes", "7" to "Auvergne-Rhône-Alpes",
    "08" to "Grand Est", "8" to "Grand Est",
    "09" to "Occitanie", "9" to "Occitanie",
    "10" to "Grand Est",
    "11" to "Occitanie",
    "12" to "Occitanie",
    "13" to "Provence-Alpes-Côte d’Azur",
    "14" to "Normandie",
    "15" to "Auvergne-Rhône-Alpes",
    "16" to "Nouvelle-Aquitaine",
    "17" to "Nouvelle-Aquitaine",
    "18" to "Centre-Val de Loire",
    "19" to "Nouvelle-Aquitaine",
    "2A" to "Corse",
    "2B" to "Corse",
    "21" to "Bourgogne-Franche-Comté",
    "22" to "Bretagne",
    "23" to "Nouvelle-Aquitaine",
    "24" to "Nouvelle-Aquitaine",
    "25" to "Bourgogne-Franche-Comté",
    "26" to "Auvergne-Rhône-Alpes",
    "27" to "Normandie",
    "28" to "Centre-Val de Loire",
    "29" to "Bretagne",
    "30" to "Occitanie",
    "31" to "Occitanie",
    "32" to "Occitanie",
    "33" to "Nouvelle-Aquitaine",
    "34" to "Occitanie",
    "35" to "Bretagne",
    "36" to "Centre-Val de Loire",
    "37" to "Centre-Val de Loire",
    "38" to "Auvergne-Rhône-Alpes",
    "39" to "Bourgogne-Franche-Comté",
    "40" to "Nouvelle-Aquitaine",
    "41" to "Centre-Val de Loire",
    "42" to "Auvergne-Rhône-Alpes",
    "43" to "Auvergne-Rhône-Alpes",
    "44" to "Pays de la Loire",
    "45" to "Centre-Val de Loire",
    "46" to "Occitanie",
    "47" to "Nouvelle-Aquitaine",
    "48" to "Occitanie",
    "49" to "Pays de la Loire",
    "50" to "Normandie",
    "51" to "Grand Est",
    "52" to "Grand Est",
    "53" to "Pays de la Loire",
    "54" to "Grand Est",
    "55" to "Grand Est",
    "56" to "Bretagne",
    "57" to "Grand Est",
    "58" to "Bourgogne-Franche-Comté",
    "59" to "Hauts-de-France",
    "60" to "Hauts-de-France",
    "61" to "Normandie",
    "62" to "Hauts-de-France",
    "63" to "Auvergne-Rhône-Alpes",
    "64" to "Nouvelle-Aquitaine",
    "65" to "Occitanie",
    "66" to "Occitanie",
    "67" to "Grand Est",
    "68" to "Grand Est",
    "69" to "Auvergne-Rhône-Alpes",
    "70" to "Bourgogne-Franche-Comté",
    "71" to "Bourgogne-Franche-Comté",
    "72" to "Pays de la Loire",
    "73" to "Auvergne-Rhône-Alpes",
    "74" to "Auvergne-Rhône-Alpes",
    "75" to "Île-de-France",
    "76" to "Normandie",
    "77" to "Île-de-France",
    "78" to "Île-de-France",
    "79" to "Nouvelle-Aquitaine",
    "80" to "Hauts-de-France",
    "81" to "Occitanie",
    "82" to "Occitanie",
    "83" to "Provence-Alpes-Côte d’Azur",
    "84" to "Provence-Alpes-Côte d’Azur",
    "85" to "Pays de la Loire",
    "86" to "Nouvelle-Aquitaine",
    "87" to "Nouvelle-Aquitaine",
    "88" to "Grand Est",
    "89" to "Bourgogne-Franche-Comté",
    "90" to "Bourgogne-Franche-Comté",
    "91" to "Île-de-France",
    "92" to "Île-de-France",
    "93" to "Île-de-France",
    "94" to "Île-de-France",
    "95" to "Île-de-France",
    "971" to "Guadeloupe",
    "972" to "Martinique",
    "973" to "Guyane",
    "974" to "La Réunion",
    "975" to "Saint-Pierre-et-Miquelon",
    "976" to "Mayotte",
    "977" to "Saint-Barthélemy et Saint-Martin",
    "978" to "Saint-Barthélemy et Saint-Martin",
    "986" to "Wallis-et-Futuna",
    "987" to "Polynésie française",
    "988" to "Nouvelle-Calédonie",
    "99" to "Français établis hors de France",
    "099" to "Français établis hors de France"
)

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

private fun JSONObject.optCleanString(key: String): String? = optText(key).trim().takeIf { it.isNotBlank() }

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
