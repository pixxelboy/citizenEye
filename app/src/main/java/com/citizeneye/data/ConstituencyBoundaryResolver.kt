package com.citizeneye.data

import org.json.JSONArray
import org.json.JSONObject

private const val BOUNDARIES_URL = "https://static.data.gouv.fr/resources/contours-geographiques-des-circonscriptions-legislatives/20240613-191520/circonscriptions-legislatives-p10.geojson"
private const val BOUNDARIES_CACHE_KEY = "circonscriptions-legislatives-p10-20240613.geojson"

data class ConstituencyBoundary(
    val departmentCode: String,
    val constituencyNumber: String,
    val code: String,
    val name: String
)

class ConstituencyBoundaryClient(
    private val publicDataCache: PublicDataCache? = null
) {
    private var resolverCache: ConstituencyBoundaryResolver? = null

    fun resolve(latitude: Double, longitude: Double): ConstituencyBoundary? {
        val resolver = resolverCache ?: ConstituencyBoundaryResolver.fromGeoJson(
            (publicDataCache?.getBytes(BOUNDARIES_URL, BOUNDARIES_CACHE_KEY) ?: httpGetBytes(BOUNDARIES_URL)).toString(Charsets.UTF_8)
        ).also { resolverCache = it }
        return resolver.resolve(latitude, longitude)
    }
}

class ConstituencyBoundaryResolver private constructor(
    private val boundaries: List<ParsedBoundary>
) {
    fun resolve(latitude: Double, longitude: Double): ConstituencyBoundary? {
        val point = GeoPoint(longitude, latitude)
        return boundaries.firstOrNull { boundary ->
            point.x in boundary.minX..boundary.maxX &&
                point.y in boundary.minY..boundary.maxY &&
                boundary.polygons.any { polygon -> polygon.contains(point) }
        }?.boundary
    }

    companion object {
        fun fromGeoJson(geoJson: String): ConstituencyBoundaryResolver {
            val features = JSONObject(geoJson).optJSONArray("features") ?: JSONArray()
            val boundaries = (0 until features.length()).mapNotNull { index ->
                val feature = features.optJSONObject(index) ?: return@mapNotNull null
                val props = feature.optJSONObject("properties") ?: return@mapNotNull null
                val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
                val code = props.optString("codeCirconscription")
                val department = props.optString("codeDepartement")
                val number = constituencyNumberFromCode(code, department).ifBlank { props.optString("numCirco") }
                val boundary = ConstituencyBoundary(
                    departmentCode = department,
                    constituencyNumber = number,
                    code = code,
                    name = props.optString("nomCirconscription", "${number}e circonscription")
                )
                val polygons = polygonsFromGeometry(geometry)
                if (polygons.isEmpty()) null else ParsedBoundary(boundary, polygons)
            }
            return ConstituencyBoundaryResolver(boundaries)
        }

        private fun polygonsFromGeometry(geometry: JSONObject): List<GeoPolygon> = when (geometry.optString("type")) {
            "Polygon" -> listOfNotNull(polygonFromRings(geometry.optJSONArray("coordinates")))
            "MultiPolygon" -> {
                val multi = geometry.optJSONArray("coordinates") ?: return emptyList()
                (0 until multi.length()).mapNotNull { polygonFromRings(multi.optJSONArray(it)) }
            }
            else -> emptyList()
        }

        private fun polygonFromRings(rings: JSONArray?): GeoPolygon? {
            if (rings == null || rings.length() == 0) return null
            val parsedRings = (0 until rings.length()).mapNotNull { ringIndex ->
                val ring = rings.optJSONArray(ringIndex) ?: return@mapNotNull null
                (0 until ring.length()).mapNotNull { pointIndex ->
                    val point = ring.optJSONArray(pointIndex) ?: return@mapNotNull null
                    GeoPoint(point.optDouble(0), point.optDouble(1))
                }.takeIf { it.size >= 3 }
            }
            if (parsedRings.isEmpty()) return null
            return GeoPolygon(parsedRings)
        }

        private fun constituencyNumberFromCode(code: String, department: String): String {
            val suffix = code.removePrefix(department)
            return suffix.toIntOrNull()?.toString().orEmpty()
        }
    }
}

private data class ParsedBoundary(
    val boundary: ConstituencyBoundary,
    val polygons: List<GeoPolygon>
) {
    val minX = polygons.minOf { it.minX }
    val maxX = polygons.maxOf { it.maxX }
    val minY = polygons.minOf { it.minY }
    val maxY = polygons.maxOf { it.maxY }
}

private data class GeoPoint(val x: Double, val y: Double)

private class GeoPolygon(private val rings: List<List<GeoPoint>>) {
    val minX = rings.flatten().minOf { it.x }
    val maxX = rings.flatten().maxOf { it.x }
    val minY = rings.flatten().minOf { it.y }
    val maxY = rings.flatten().maxOf { it.y }

    fun contains(point: GeoPoint): Boolean {
        if (point.x !in minX..maxX || point.y !in minY..maxY) return false
        val outer = rings.firstOrNull() ?: return false
        if (!ringContains(outer, point)) return false
        return rings.drop(1).none { hole -> ringContains(hole, point) }
    }

    private fun ringContains(ring: List<GeoPoint>, point: GeoPoint): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            val intersects = (a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / ((b.y - a.y).takeIf { it != 0.0 } ?: 1e-12) + a.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
