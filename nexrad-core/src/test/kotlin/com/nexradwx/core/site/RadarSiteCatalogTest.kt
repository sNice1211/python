package com.nexradwx.core.site

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RadarSiteCatalogTest {

    @Test
    fun `catalog has no duplicate ids and a sane count`() {
        val ids = RadarSiteCatalog.sites.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate site id found")
        assertTrue(ids.size > 150, "expected the full CONUS+territories WSR-88D network")
    }

    @Test
    fun `findById is case-insensitive`() {
        val site = assertNotNull(RadarSiteCatalog.findById("ktlx"))
        assertEquals("KTLX", site.id)
    }

    @Test
    fun `nearest returns Oklahoma City site for a point right on top of it`() {
        val nearest = RadarSiteCatalog.nearest(35.333, -97.278, count = 1).single()
        assertEquals("KTLX", nearest.id)
    }

    @Test
    fun `nearest orders by increasing distance`() {
        val results = RadarSiteCatalog.nearest(35.333, -97.278, count = 5)
        val distances = results.map { it.distanceKmTo(35.333, -97.278) }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `search filters by id substring`() {
        val results = RadarSiteCatalog.search("TLX")
        assertTrue(results.any { it.id == "KTLX" })
        assertTrue(results.all { it.id.contains("TLX") })
    }
}
