package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BaseMapParseTest {

    @Test
    fun testParseGeoJson() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[76.38, 9.38], [76.39, 9.39]]
                  },
                  "properties": {
                    "highway": "primary"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[76.40, 9.40], [76.41, 9.41]]
                  },
                  "properties": {
                    "highway": "residential"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[76.42, 9.42], [76.43, 9.43]]
                  },
                  "properties": {
                    "waterway": "river"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[76.44, 9.44], [76.45, 9.44], [76.45, 9.45], [76.44, 9.44]]]
                  },
                  "properties": {
                    "natural": "water"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [76.46, 9.46]
                  },
                  "properties": {
                    "place": "town",
                    "name": "Kochi"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [76.47, 9.47]
                  },
                  "properties": {
                    "amenity": "hospital",
                    "name": "General Hospital"
                  }
                },
                {
                  "type": "Feature",
                  "properties": {
                    "invalid": "feature"
                  }
                }
              ]
            }
        """.trimIndent()

        val data = parseGeoJson(json)

        assertEquals(2, data.roads.size)
        assertEquals(1, data.roads.count { it.roadClass == RoadClass.MAJOR })
        assertEquals(1, data.roads.count { it.roadClass == RoadClass.MINOR })
        assertEquals(1, data.waterLines.size)
        assertEquals(1, data.waterPolygons.size)

        assertEquals(1, data.places.size)
        assertEquals("Kochi", data.places[0].name)
        assertEquals("town", data.places[0].kind)

        assertEquals(1, data.facilities.size)
        assertEquals("General Hospital", data.facilities[0].name)
        assertEquals(FacilityKind.HOSPITAL, data.facilities[0].kind)

        assertEquals(1, data.skippedCount)
    }

    @Test
    fun testNestedTagsAndMerge() {
        val json1 = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [76.50, 9.50]
                  },
                  "properties": {
                    "tags": {
                      "place": "village",
                      "name": "Aluva"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val data1 = parseGeoJson(json1)
        assertEquals(1, data1.places.size)
        assertEquals("Aluva", data1.places[0].name)

        val merged = data1.merge(BaseMapData(skippedCount = 2))
        assertEquals(1, merged.places.size)
        assertEquals(2, merged.skippedCount)
    }
}
