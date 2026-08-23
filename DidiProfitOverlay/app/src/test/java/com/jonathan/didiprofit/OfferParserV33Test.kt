package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfferParserV33Test {

    @Test
    fun reconstructsSplitRouteRowsDuringActiveTrip() {
        val lines = listOf(
            OcrLine("$36.39", 720, 76, 120, 48, 220),
            OcrLine("$9.48 de tarifa base dinámica", 920, 28, 220, 50, 390),
            OcrLine("10min", 1170, 32, 102, 48, 150),
            OcrLine("(2.7km)", 1172, 31, 220, 165, 280),
            OcrLine("6min", 1305, 32, 95, 48, 140),
            OcrLine("(2.8km)", 1308, 31, 215, 160, 275)
        )

        val offer = assertNotNull(OfferParser.parse(lines))
        assertEquals(36.39, offer.fare, 0.001)
        assertEquals(16, offer.totalMinutes)
        assertEquals(5.5, offer.totalKilometers, 0.001)
    }

    @Test
    fun activeNavigationNoiseDoesNotReplaceOfferRoutes() {
        val lines = listOf(
            OcrLine("171 m", 120, 70, 170, 70, 260),
            OcrLine("And. Atlántico", 210, 35, 210, 90, 360),
            OcrLine("$84.38", 650, 80, 135, 45, 245),
            OcrLine("5min", 1000, 31, 90, 45, 140),
            OcrLine("(699m)", 1002, 31, 215, 160, 275),
            OcrLine("22min", 1130, 31, 100, 45, 150),
            OcrLine("(12.2km)", 1132, 31, 230, 170, 300)
        )

        assertTrue(OfferParser.looksLikeOffer(lines))
        val offer = assertNotNull(OfferParser.parse(lines))
        assertEquals(84.38, offer.fare, 0.001)
        assertEquals(27, offer.totalMinutes)
        assertEquals(12.899, offer.totalKilometers, 0.001)
    }

    @Test
    fun parsesThirdReportedActiveTripFailure() {
        val lines = listOf(
            OcrLine("$39.27", 690, 80, 140, 45, 245),
            OcrLine("7min", 955, 31, 90, 45, 140),
            OcrLine("(1.5km)", 957, 31, 210, 160, 270),
            OcrLine("9min", 1090, 31, 90, 45, 140),
            OcrLine("(3.5km)", 1092, 31, 210, 160, 270)
        )

        val offer = assertNotNull(OfferParser.parse(lines))
        assertEquals(16, offer.totalMinutes)
        assertEquals(5.0, offer.totalKilometers, 0.001)
    }
}
