package com.citizeneye.data

/**
 * Centralized, source-visible presentation config for parliamentary group families.
 *
 * Political family labels are descriptive categories configured by CitizenEye, not AI inference
 * and not a score. Group names and abbreviations come from Assemblée nationale public data.
 */
data class PoliticalFamilyConfig(
    val groupCode: String,
    val groupName: String,
    val politicalFamily: String,
    val colorHex: String,
    val officialAssemblyUrl: String
)

object PoliticalFamilies {
    private val configs = listOf(
        PoliticalFamilyConfig("LFI", "La France insoumise - Nouveau Front Populaire", "Gauche", "#6B5B95", officialGroupUrl("La France insoumise")),
        PoliticalFamilyConfig("SOC", "Socialistes et apparentés", "Centre gauche", "#B85C7A", officialGroupUrl("Socialistes et apparentés")),
        PoliticalFamilyConfig("ECO", "Écologiste et Social", "Gauche", "#5B8F68", officialGroupUrl("Écologiste et Social")),
        PoliticalFamilyConfig("GDR", "Gauche Démocrate et Républicaine", "Gauche", "#7A6A58", officialGroupUrl("Gauche Démocrate et Républicaine")),
        PoliticalFamilyConfig("REN", "Renaissance", "Centre", "#5B7FA8", officialGroupUrl("Renaissance")),
        PoliticalFamilyConfig("EPR", "Ensemble pour la République", "Centre", "#5B7FA8", officialGroupUrl("Ensemble pour la République")),
        PoliticalFamilyConfig("DEM", "Les Démocrates", "Centre", "#5B8FA8", officialGroupUrl("Les Démocrates")),
        PoliticalFamilyConfig("HOR", "Horizons & Indépendants", "Centre droit", "#6F8192", officialGroupUrl("Horizons & Indépendants")),
        PoliticalFamilyConfig("LIOT", "Libertés, Indépendants, Outre-mer et Territoires", "Divers", "#7C7F86", officialGroupUrl("Libertés Indépendants Outre-mer Territoires")),
        PoliticalFamilyConfig("LR", "Droite Républicaine", "Droite", "#4F6FA8", officialGroupUrl("Droite Républicaine")),
        PoliticalFamilyConfig("UDR", "Union des Droites pour la République", "Droite", "#596273", officialGroupUrl("Union des Droites pour la République")),
        PoliticalFamilyConfig("RN", "Rassemblement National", "Extrême droite", "#475569", officialGroupUrl("Rassemblement National")),
        PoliticalFamilyConfig("NI", "Non inscrits", "Non rattaché", "#69717D", officialGroupUrl("Non inscrits")),
        PoliticalFamilyConfig("N/R", "Groupe non renseigné", "Non renseignée", "#69717D", "https://www.assemblee-nationale.fr/dyn/groupes-politiques")
    )

    fun all(): List<PoliticalFamilyConfig> = configs

    fun forDeputy(depute: Depute): PoliticalFamilyConfig {
        val code = depute.displayPoliticalGroupShort.normalizeGroupCode()
        return configs.firstOrNull { it.groupCode.normalizeGroupCode() == code }
            ?: configs.firstOrNull { config ->
                depute.displayPoliticalGroupFull.contains(config.groupName, ignoreCase = true) ||
                    config.groupName.contains(depute.displayPoliticalGroupFull, ignoreCase = true)
            }
            ?: PoliticalFamilyConfig(
                groupCode = depute.displayPoliticalGroupShort,
                groupName = depute.displayPoliticalGroupFull,
                politicalFamily = "Information non disponible depuis les sources officielles.",
                colorHex = "#69717D",
                officialAssemblyUrl = officialGroupUrl(depute.displayPoliticalGroupFull)
            )
    }

    private fun String.normalizeGroupCode(): String = trim().uppercase()

    private fun officialGroupUrl(query: String): String =
        "https://www.assemblee-nationale.fr/dyn/recherche?search=" + java.net.URLEncoder.encode(query, "UTF-8")
}
