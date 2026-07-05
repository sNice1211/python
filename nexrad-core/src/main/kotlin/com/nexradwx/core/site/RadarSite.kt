package com.nexradwx.core.site

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A WSR-88D (NEXRAD) radar site. Coordinates and elevation are sourced from NOAA's Historical
 * Observing Metadata Repository (HOMR) via the py-ART project's nexrad_common.py (BSD-3), plus
 * TJUA and the overseas military WSR-88D units (LPLA, RKJK, RKSG, RODN). [elevationMeters] is
 * null where HOMR has no reliable value.
 */
data class RadarSite(
    val id: String,
    val latitude: Float,
    val longitude: Float,
    val elevationMeters: Int?,
) {
    /** Great-circle distance to a point, in kilometers (haversine). */
    fun distanceKmTo(lat: Double, lon: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat - latitude)
        val dLon = Math.toRadians(lon - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(latitude.toDouble())) * cos(Math.toRadians(lat)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }
}

object RadarSiteCatalog {
    val sites: List<RadarSite> = listOf(
    RadarSite("KABR", 45.45583f, -98.41306f, 397),
    RadarSite("KABX", 35.14972f, -106.82333f, 1789),
    RadarSite("KAKQ", 36.98389f, -77.0075f, 34),
    RadarSite("KAMA", 35.23333f, -101.70889f, 1093),
    RadarSite("KAMX", 25.61056f, -80.41306f, 4),
    RadarSite("KAPX", 44.90722f, -84.71972f, 446),
    RadarSite("KARX", 43.82278f, -91.19111f, 389),
    RadarSite("KATX", 48.19472f, -122.49444f, 151),
    RadarSite("KBBX", 39.49611f, -121.63167f, 53),
    RadarSite("KBGM", 42.19972f, -75.985f, 490),
    RadarSite("KBHX", 40.49833f, -124.29194f, 732),
    RadarSite("KBIS", 46.77083f, -100.76028f, 505),
    RadarSite("KBLX", 45.85389f, -108.60611f, 1097),
    RadarSite("KBMX", 33.17194f, -86.76972f, 197),
    RadarSite("KBOX", 41.95583f, -71.1375f, 36),
    RadarSite("KBRO", 25.91556f, -97.41861f, 7),
    RadarSite("KBUF", 42.94861f, -78.73694f, 211),
    RadarSite("KBYX", 24.59694f, -81.70333f, 2),
    RadarSite("KCAE", 33.94861f, -81.11861f, 70),
    RadarSite("KCBW", 46.03917f, -67.80694f, 227),
    RadarSite("KCBX", 43.49083f, -116.23444f, 933),
    RadarSite("KCCX", 40.92306f, -78.00389f, 733),
    RadarSite("KCLE", 41.41306f, -81.86f, 233),
    RadarSite("KCLX", 32.65556f, -81.04222f, 30),
    RadarSite("KCRI", 35.2383f, -97.4602f, 366),
    RadarSite("KCRP", 27.78389f, -97.51083f, 14),
    RadarSite("KCXX", 44.51111f, -73.16639f, 97),
    RadarSite("KCYS", 41.15194f, -104.80611f, 1868),
    RadarSite("KDAX", 38.50111f, -121.67667f, 9),
    RadarSite("KDDC", 37.76083f, -99.96833f, 789),
    RadarSite("KDFX", 29.2725f, -100.28028f, 345),
    RadarSite("KDGX", 32.28f, -89.98444f, null),
    RadarSite("KDIX", 39.94694f, -74.41111f, 45),
    RadarSite("KDLH", 46.83694f, -92.20972f, 435),
    RadarSite("KDMX", 41.73111f, -93.72278f, 299),
    RadarSite("KDOX", 38.82556f, -75.44f, 15),
    RadarSite("KDTX", 42.69972f, -83.47167f, 327),
    RadarSite("KDVN", 41.61167f, -90.58083f, 230),
    RadarSite("KDYX", 32.53833f, -99.25417f, 462),
    RadarSite("KEAX", 38.81028f, -94.26417f, 303),
    RadarSite("KEMX", 31.89361f, -110.63028f, 1586),
    RadarSite("KENX", 42.58639f, -74.06444f, 557),
    RadarSite("KEOX", 31.46028f, -85.45944f, 132),
    RadarSite("KEPZ", 31.87306f, -106.6975f, 1251),
    RadarSite("KESX", 35.70111f, -114.89139f, 1483),
    RadarSite("KEVX", 30.56417f, -85.92139f, 43),
    RadarSite("KEWX", 29.70361f, -98.02806f, 193),
    RadarSite("KEYX", 35.09778f, -117.56f, 840),
    RadarSite("KFCX", 37.02417f, -80.27417f, 874),
    RadarSite("KFDR", 34.36222f, -98.97611f, 386),
    RadarSite("KFDX", 34.63528f, -103.62944f, 1417),
    RadarSite("KFFC", 33.36333f, -84.56583f, 262),
    RadarSite("KFSD", 43.58778f, -96.72889f, 436),
    RadarSite("KFSX", 34.57444f, -111.19833f, null),
    RadarSite("KFTG", 39.78667f, -104.54528f, 1675),
    RadarSite("KFWS", 32.57278f, -97.30278f, 208),
    RadarSite("KGGW", 48.20639f, -106.62417f, 694),
    RadarSite("KGJX", 39.06222f, -108.21306f, 3046),
    RadarSite("KGLD", 39.36694f, -101.7f, 1113),
    RadarSite("KGRB", 44.49833f, -88.11111f, 208),
    RadarSite("KGRK", 30.72167f, -97.38278f, 164),
    RadarSite("KGRR", 42.89389f, -85.54472f, 237),
    RadarSite("KGSP", 34.88306f, -82.22028f, 287),
    RadarSite("KGWX", 33.89667f, -88.32889f, 145),
    RadarSite("KGYX", 43.89139f, -70.25694f, 125),
    RadarSite("KHDC", 30.519f, -90.407f, 13),
    RadarSite("KHDX", 33.07639f, -106.12222f, 1287),
    RadarSite("KHGX", 29.47194f, -95.07889f, 5),
    RadarSite("KHNX", 36.31417f, -119.63111f, 74),
    RadarSite("KHPX", 36.73667f, -87.285f, 176),
    RadarSite("KHTX", 34.93056f, -86.08361f, 536),
    RadarSite("KICT", 37.65444f, -97.4425f, 407),
    RadarSite("KICX", 37.59083f, -112.86222f, 3231),
    RadarSite("KILN", 39.42028f, -83.82167f, 322),
    RadarSite("KILX", 40.15056f, -89.33667f, 177),
    RadarSite("KIND", 39.7075f, -86.28028f, 241),
    RadarSite("KINX", 36.175f, -95.56444f, 204),
    RadarSite("KIWA", 33.28917f, -111.66917f, 412),
    RadarSite("KIWX", 41.40861f, -85.7f, 293),
    RadarSite("KJAX", 30.48444f, -81.70194f, 10),
    RadarSite("KJGX", 32.675f, -83.35111f, 159),
    RadarSite("KJKL", 37.59083f, -83.31306f, 416),
    RadarSite("KLBB", 33.65417f, -101.81361f, 993),
    RadarSite("KLCH", 30.125f, -93.21583f, 4),
    RadarSite("KLGX", 47.1158f, -124.1069f, 77),
    RadarSite("KLIX", 30.33667f, -89.82528f, 7),
    RadarSite("KLNX", 41.95778f, -100.57583f, 905),
    RadarSite("KLOT", 41.60444f, -88.08472f, 202),
    RadarSite("KLRX", 40.73972f, -116.80278f, 2056),
    RadarSite("KLSX", 38.69889f, -90.68278f, 185),
    RadarSite("KLTX", 33.98917f, -78.42917f, 20),
    RadarSite("KLVX", 37.97528f, -85.94389f, 219),
    RadarSite("KLWX", 38.97628f, -77.48751f, null),
    RadarSite("KLZK", 34.83639f, -92.26194f, 173),
    RadarSite("KMAF", 31.94333f, -102.18889f, 874),
    RadarSite("KMAX", 42.08111f, -122.71611f, 2290),
    RadarSite("KMBX", 48.3925f, -100.86444f, 455),
    RadarSite("KMHX", 34.77583f, -76.87639f, 9),
    RadarSite("KMKX", 42.96778f, -88.55056f, 292),
    RadarSite("KMLB", 28.11306f, -80.65444f, 30),
    RadarSite("KMOB", 30.67944f, -88.23972f, 63),
    RadarSite("KMPX", 44.84889f, -93.56528f, 288),
    RadarSite("KMQT", 46.53111f, -87.54833f, 430),
    RadarSite("KMRX", 36.16833f, -83.40194f, 408),
    RadarSite("KMSX", 47.04111f, -113.98611f, 2394),
    RadarSite("KMTX", 41.26278f, -112.44694f, 1969),
    RadarSite("KMUX", 37.15528f, -121.8975f, 1057),
    RadarSite("KMVX", 47.52806f, -97.325f, 301),
    RadarSite("KMXX", 32.53667f, -85.78972f, 122),
    RadarSite("KNKX", 32.91889f, -117.04194f, 291),
    RadarSite("KNQA", 35.34472f, -89.87333f, 86),
    RadarSite("KOAX", 41.32028f, -96.36639f, 350),
    RadarSite("KOHX", 36.24722f, -86.5625f, 176),
    RadarSite("KOKX", 40.86556f, -72.86444f, 26),
    RadarSite("KOTX", 47.68056f, -117.62583f, 727),
    RadarSite("KPAH", 37.06833f, -88.77194f, 119),
    RadarSite("KPBZ", 40.53167f, -80.21833f, 361),
    RadarSite("KPDT", 45.69056f, -118.85278f, 462),
    RadarSite("KPOE", 31.15528f, -92.97583f, 124),
    RadarSite("KPUX", 38.45944f, -104.18139f, 1600),
    RadarSite("KRAX", 35.66528f, -78.49f, 106),
    RadarSite("KRGX", 39.75417f, -119.46111f, 2530),
    RadarSite("KRIW", 43.06611f, -108.47667f, 1697),
    RadarSite("KRLX", 38.31194f, -81.72389f, 329),
    RadarSite("KRTX", 45.715f, -122.96417f, null),
    RadarSite("KSFX", 43.10583f, -112.68528f, 1364),
    RadarSite("KSGF", 37.23528f, -93.40028f, 390),
    RadarSite("KSHV", 32.45056f, -93.84111f, 83),
    RadarSite("KSJT", 31.37111f, -100.49222f, 576),
    RadarSite("KSOX", 33.81778f, -117.635f, 923),
    RadarSite("KSRX", 35.29056f, -94.36167f, null),
    RadarSite("KTBW", 27.70528f, -82.40194f, 12),
    RadarSite("KTFX", 47.45972f, -111.38444f, 1132),
    RadarSite("KTLH", 30.3975f, -84.32889f, 19),
    RadarSite("KTLX", 35.33306f, -97.2775f, 370),
    RadarSite("KTWX", 38.99694f, -96.2325f, 417),
    RadarSite("KTYX", 43.75583f, -75.68f, 563),
    RadarSite("KUDX", 44.125f, -102.82944f, 919),
    RadarSite("KUEX", 40.32083f, -98.44167f, 602),
    RadarSite("KVAX", 30.89f, -83.00194f, 54),
    RadarSite("KVBX", 34.83806f, -120.39583f, 376),
    RadarSite("KVNX", 36.74083f, -98.1275f, 369),
    RadarSite("KVTX", 34.41167f, -119.17861f, 831),
    RadarSite("KVWX", 38.26f, -87.7247f, null),
    RadarSite("KYUX", 32.49528f, -114.65583f, 53),
    RadarSite("LPLA", 38.73028f, -27.32167f, 1016),
    RadarSite("PABC", 60.79278f, -161.87417f, 49),
    RadarSite("PACG", 56.85278f, -135.52917f, 82),
    RadarSite("PAEC", 64.51139f, -165.295f, 16),
    RadarSite("PAHG", 60.725914f, -151.35146f, 74),
    RadarSite("PAIH", 59.46194f, -146.30111f, 20),
    RadarSite("PAKC", 58.67944f, -156.62944f, 19),
    RadarSite("PAPD", 65.03556f, -147.49917f, 790),
    RadarSite("PGUA", 13.45444f, 144.80833f, 80),
    RadarSite("PHKI", 21.89417f, -159.55222f, 55),
    RadarSite("PHKM", 20.12556f, -155.77778f, 1162),
    RadarSite("PHMO", 21.13278f, -157.18f, 415),
    RadarSite("PHWA", 19.095f, -155.56889f, 418),
    RadarSite("RKJK", 35.92417f, 126.62222f, 24),
    RadarSite("RKSG", 36.95972f, 127.01833f, 16),
    RadarSite("RODN", 26.30194f, 127.90972f, 66),
    RadarSite("TJUA", 18.1175f, -66.07861f, 852),
    )

    private val byId: Map<String, RadarSite> = sites.associateBy { it.id }

    fun findById(id: String): RadarSite? = byId[id.uppercase()]

    fun nearest(lat: Double, lon: Double, count: Int = 10): List<RadarSite> =
        sites.sortedBy { it.distanceKmTo(lat, lon) }.take(count)

    fun search(query: String): List<RadarSite> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return sites
        return sites.filter { it.id.contains(q) }
    }
}
