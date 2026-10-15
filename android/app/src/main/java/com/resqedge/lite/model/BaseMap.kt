package com.resqedge.lite.model

import org.json.JSONArray
import org.json.JSONObject

enum class RoadClass { MAJOR, MINOR }

enum class FacilityKind { HOSPITAL, CLINIC, SCHOOL }

data class LatLon(val lat: Double, val lon: Double)

data class Road(val points: List<LatLon>, val roadClass: RoadClass)

data class WaterPolygon(val outer: List<LatLon>, val inners: List<List<LatLon>> = emptyList())

data class WaterLine(val points: List<LatLon>)

data class Place(val name: String, val kind: String, val lat: Double, val lon: Double)

data class Facility(val name: String?, val kind: FacilityKind, val lat: Double, val lon: Double)

data class BaseMapData(
    val roads: List<Road> = emptyList(),
    val waterPolygons: List<WaterPolygon> = emptyList(),
    val waterLines: List<WaterLine> = emptyList(),
    val places: List<Place> = emptyList(),
    val facilities: List<Facility> = emptyList(),
    val skippedCount: Int = 0
) {
    val isEmpty: Boolean
        get() = roads.isEmpty() && waterPolygons.isEmpty() && waterLines.isEmpty() &&
                places.isEmpty() && facilities.isEmpty()

    fun merge(other: BaseMapData): BaseMapData {
        return BaseMapData(
            roads = this.roads + other.roads,
            waterPolygons = this.waterPolygons + other.waterPolygons,
            waterLines = this.waterLines + other.waterLines,
            places = this.places + other.places,
            facilities = this.facilities + other.facilities,
            skippedCount = this.skippedCount + other.skippedCount
        )
    }
}

fun parseGeoJson(text: String): BaseMapData {
    val roads = mutableListOf<Road>()
    val waterPolygons = mutableListOf<WaterPolygon>()
    val waterLines = mutableListOf<WaterLine>()
    val places = mutableListOf<Place>()
    val facilities = mutableListOf<Facility>()
    var skipped = 0

    try {
        val root = JSONObject(text)
        val features = if (root.optString("type") == "FeatureCollection") {
            root.optJSONArray("features") ?: JSONArray()
        } else if (root.optString("type") == "Feature") {
            JSONArray().apply { put(root) }
        } else {
            return BaseMapData(skippedCount = 1)
        }

        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i)
            if (feat == null || feat.optString("type") != "Feature") {
                skipped++
                continue
            }

            val geom = feat.optJSONObject("geometry")
            val props = feat.optJSONObject("properties")
            if (geom == null || props == null) {
                skipped++
                continue
            }

            var handled = false
            try {
                val highway = getTag(props, "highway")
                val waterway = getTag(props, "waterway")
                val natural = getTag(props, "natural")
                val placeTag = getTag(props, "place")
                val amenity = getTag(props, "amenity")

                if (highway != null) {
                    val roadClass = when (highway.lowercase()) {
                        "motorway", "trunk", "primary", "secondary" -> RoadClass.MAJOR
                        "tertiary", "unclassified", "residential", "living_street", "service", "track", "path", "footway" -> RoadClass.MINOR
                        else -> RoadClass.MINOR
                    }
                    val lines = parseLineStrings(geom)
                    if (lines.isNotEmpty()) {
                        for (line in lines) {
                            if (line.size >= 2) roads.add(Road(line, roadClass))
                        }
                        handled = true
                    }
                } else if (waterway != null) {
                    val lines = parseLineStrings(geom)
                    if (lines.isNotEmpty()) {
                        for (line in lines) {
                            if (line.size >= 2) waterLines.add(WaterLine(line))
                        }
                        handled = true
                    }
                } else if (natural == "water") {
                    val polygons = parsePolygons(geom)
                    if (polygons.isNotEmpty()) {
                        waterPolygons.addAll(polygons)
                        handled = true
                    }
                } else if (placeTag != null) {
                    val name = getTag(props, "name")
                    val centroid = computeCentroid(geom)
                    if (name != null && name.isNotBlank() && centroid != null) {
                        places.add(Place(name, placeTag, centroid.lat, centroid.lon))
                        handled = true
                    }
                } else if (amenity != null) {
                    val kind = when (amenity.lowercase()) {
                        "hospital" -> FacilityKind.HOSPITAL
                        "clinic" -> FacilityKind.CLINIC
                        "school" -> FacilityKind.SCHOOL
                        else -> null
                    }
                    val centroid = computeCentroid(geom)
                    if (kind != null && centroid != null) {
                        val name = getTag(props, "name")
                        facilities.add(Facility(name, kind, centroid.lat, centroid.lon))
                        handled = true
                    }
                }
            } catch (_: Exception) {
                handled = false
            }

            if (!handled) {
                skipped++
            }
        }
    } catch (_: Exception) {
        skipped++
    }

    return BaseMapData(roads, waterPolygons, waterLines, places, facilities, skipped)
}

private fun getTag(props: JSONObject, key: String): String? {
    val direct = props.optString(key, "")
    if (direct.isNotEmpty() && direct != "null") return direct
    val tags = props.optJSONObject("tags")
    val nested = tags?.optString(key, "")
    if (nested != null && nested.isNotEmpty() && nested != "null") return nested
    return null
}

private fun parseLatLon(coord: JSONArray?): LatLon? {
    if (coord == null || coord.length() < 2) return null
    val lon = coord.optDouble(0, Double.NaN)
    val lat = coord.optDouble(1, Double.NaN)
    if (lat.isNaN() || lon.isNaN() || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
    return LatLon(lat, lon)
}

private fun parsePointList(array: JSONArray?): List<LatLon> {
    if (array == null) return emptyList()
    val list = ArrayList<LatLon>(array.length())
    for (i in 0 until array.length()) {
        val pt = array.optJSONArray(i) ?: continue
        val latLon = parseLatLon(pt) ?: continue
        list.add(latLon)
    }
    return list
}

private fun parseLineStrings(geom: JSONObject): List<List<LatLon>> {
    val type = geom.optString("type", "")
    val coords = geom.optJSONArray("coordinates") ?: return emptyList()
    return when (type) {
        "LineString" -> {
            val list = parsePointList(coords)
            if (list.isNotEmpty()) listOf(list) else emptyList()
        }
        "MultiLineString" -> {
            val out = mutableListOf<List<LatLon>>()
            for (i in 0 until coords.length()) {
                val line = parsePointList(coords.optJSONArray(i))
                if (line.isNotEmpty()) out.add(line)
            }
            out
        }
        else -> emptyList()
    }
}

private fun parsePolygons(geom: JSONObject): List<WaterPolygon> {
    val type = geom.optString("type", "")
    val coords = geom.optJSONArray("coordinates") ?: return emptyList()
    return when (type) {
        "Polygon" -> {
            val outer = parsePointList(coords.optJSONArray(0))
            if (outer.isEmpty()) return emptyList()
            val inners = mutableListOf<List<LatLon>>()
            for (i in 1 until coords.length()) {
                val inner = parsePointList(coords.optJSONArray(i))
                if (inner.isNotEmpty()) inners.add(inner)
            }
            listOf(WaterPolygon(outer, inners))
        }
        "MultiPolygon" -> {
            val out = mutableListOf<WaterPolygon>()
            for (i in 0 until coords.length()) {
                val polyCoords = coords.optJSONArray(i) ?: continue
                val outer = parsePointList(polyCoords.optJSONArray(0))
                if (outer.isEmpty()) continue
                val inners = mutableListOf<List<LatLon>>()
                for (j in 1 until polyCoords.length()) {
                    val inner = parsePointList(polyCoords.optJSONArray(j))
                    if (inner.isNotEmpty()) inners.add(inner)
                }
                out.add(WaterPolygon(outer, inners))
            }
            out
        }
        else -> emptyList()
    }
}

private fun computeCentroid(geom: JSONObject): LatLon? {
    val type = geom.optString("type", "")
    val coords = geom.optJSONArray("coordinates") ?: return null
    when (type) {
        "Point" -> return parseLatLon(coords)
        "LineString" -> {
            val pts = parsePointList(coords)
            if (pts.isEmpty()) return null
            return LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())
        }
        "Polygon" -> {
            val ring = coords.optJSONArray(0) ?: return null
            val pts = parsePointList(ring)
            if (pts.isEmpty()) return null
            return LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())
        }
        "MultiPolygon" -> {
            val allPts = mutableListOf<LatLon>()
            for (i in 0 until coords.length()) {
                val poly = coords.optJSONArray(i) ?: continue
                val ring = poly.optJSONArray(0) ?: continue
                allPts.addAll(parsePointList(ring))
            }
            if (allPts.isEmpty()) return null
            return LatLon(allPts.map { it.lat }.average(), allPts.map { it.lon }.average())
        }
        else -> return null
    }
}
