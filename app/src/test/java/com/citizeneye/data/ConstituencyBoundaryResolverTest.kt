package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConstituencyBoundaryResolverTest {
    @Test fun resolvesPointInsidePolygonToConstituency() {
        val resolver = ConstituencyBoundaryResolver.fromGeoJson(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"codeDepartement":"92","codeCirconscription":"9204","nomCirconscription":"4ème circonscription"},"geometry":{"type":"Polygon","coordinates":[[[2.0,48.0],[3.0,48.0],[3.0,49.0],[2.0,49.0],[2.0,48.0]]]}}
            ]}
            """.trimIndent()
        )

        assertEquals(
            ConstituencyBoundary("92", "4", "9204", "4ème circonscription"),
            resolver.resolve(latitude = 48.5, longitude = 2.5)
        )
    }

    @Test fun resolvesPointInsideMultiPolygon() {
        val resolver = ConstituencyBoundaryResolver.fromGeoJson(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"codeDepartement":"2A","codeCirconscription":"2A01","nomCirconscription":"1ère circonscription"},"geometry":{"type":"MultiPolygon","coordinates":[[[[8.0,41.0],[9.0,41.0],[9.0,42.0],[8.0,42.0],[8.0,41.0]]]]}}
            ]}
            """.trimIndent()
        )

        assertEquals("1", resolver.resolve(latitude = 41.5, longitude = 8.5)?.constituencyNumber)
    }

    @Test fun returnsNullOutsideAnyBoundary() {
        val resolver = ConstituencyBoundaryResolver.fromGeoJson(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"codeDepartement":"75","codeCirconscription":"7501","nomCirconscription":"1ère circonscription"},"geometry":{"type":"Polygon","coordinates":[[[2.0,48.0],[3.0,48.0],[3.0,49.0],[2.0,49.0],[2.0,48.0]]]}}
            ]}
            """.trimIndent()
        )

        assertNull(resolver.resolve(latitude = 50.0, longitude = 2.5))
    }
}
