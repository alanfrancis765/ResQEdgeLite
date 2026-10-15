package com.resqedge.lite.model

fun nearestFacility(
    lat: Double,
    lon: Double,
    facilities: List<Facility>,
    kinds: Set<FacilityKind> = setOf(FacilityKind.HOSPITAL, FacilityKind.CLINIC)
): Pair<Facility, Double>? {
    var best: Facility? = null
    var minDistance = Double.MAX_VALUE
    for (f in facilities) {
        if (f.kind !in kinds) continue
        val dist = Clustering.distanceMetres(lat, lon, f.lat, f.lon)
        if (dist < minDistance) {
            minDistance = dist
            best = f
        }
    }
    return if (best != null) Pair(best, minDistance) else null
}
