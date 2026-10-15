package com.resqedge.lite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacilitiesTest {

    @Test
    fun testNearestFacilityPicksClosestAndMatchesDistanceMetres() {
        val f1 = Facility("Hospital A", FacilityKind.HOSPITAL, 9.500, 76.500)
        val f2 = Facility("Clinic B", FacilityKind.CLINIC, 9.505, 76.505)
        val f3 = Facility("School C", FacilityKind.SCHOOL, 9.501, 76.501)
        val list = listOf(f1, f2, f3)

        val targetLat = 9.504
        val targetLon = 76.504

        val result = nearestFacility(targetLat, targetLon, list, setOf(FacilityKind.HOSPITAL, FacilityKind.CLINIC))

        // f2 is closer to (9.504, 76.504) than f1
        assertEquals(f2, result?.first)

        val expectedDist = Clustering.distanceMetres(targetLat, targetLon, f2.lat, f2.lon)
        assertEquals(expectedDist, result?.second ?: 0.0, 0.001)
    }

    @Test
    fun testNearestFacilityReturnsNullWhenNoneMatch() {
        val list = listOf(
            Facility("School C", FacilityKind.SCHOOL, 9.501, 76.501)
        )

        val result = nearestFacility(9.500, 76.500, list, setOf(FacilityKind.HOSPITAL, FacilityKind.CLINIC))
        assertNull(result)
    }

    @Test
    fun testNearestFacilityReturnsNullWhenListIsEmpty() {
        val result = nearestFacility(9.500, 76.500, emptyList())
        assertNull(result)
    }

    @Test
    fun testNearestFacilityIgnoresKindsNotRequested() {
        val fHospital = Facility("City Hospital", FacilityKind.HOSPITAL, 9.500, 76.500)
        val fSchool = Facility("City School", FacilityKind.SCHOOL, 9.5001, 76.5001)

        val resultSchoolOnly = nearestFacility(9.500, 76.500, listOf(fHospital, fSchool), setOf(FacilityKind.SCHOOL))
        assertEquals(fSchool, resultSchoolOnly?.first)
    }
}
