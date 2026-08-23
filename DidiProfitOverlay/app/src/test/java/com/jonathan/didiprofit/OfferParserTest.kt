package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OfferParserTest {

    @Test
    fun parsesFullScreenDynamicAndAdditionalRows() {
        val raw = """
            $176.17
            $17.00 adicionales
            $10.15 de tarifa base dinámica
            6min (874m)
            34min (19.7km)
        """.trimIndent()

        val offer = assertNotNull(OfferParser.parseSimpleText(raw))
        assertEquals(176.17, offer.fare, 0.001)
        assertEquals(40, offer.totalMinutes)
        assertEquals(20.574, offer.totalKilometers, 0.001)
    }

    @Test
    fun parsesFareWhenOcrDropsCurrencySymbol() {
        val lines = listOf(
            OcrLine("30.53", 300, 72),
            OcrLine("4.95 38 viajes", 520, 24),
            OcrLine("5min (796m)", 720, 30),
            OcrLine("6min (2.2km)", 820, 30),
            OcrLine("$31.53", 1100, 40),
            OcrLine("$32.53", 1100, 40)
        )

        val offer = assertNotNull(OfferParser.parse(lines))
        assertEquals(30.53, offer.fare, 0.001)
        assertEquals(11, offer.totalMinutes)
        assertEquals(2.996, offer.totalKilometers, 0.001)
    }

    @Test
    fun parsesMetersAndKilometers() {
        val raw = """
            $30.63
            $7.74 de tarifa base dinámica
            3min (394m)
            8min (3.1km)
        """.trimIndent()

        val offer = assertNotNull(OfferParser.parseSimpleText(raw))
        assertEquals(11, offer.totalMinutes)
        assertEquals(3.494, offer.totalKilometers, 0.001)
    }

    @Test
    fun ignoresOverlayRouteSummary() {
        val lines = listOf(
            OcrLine("$50.34", 300, 70),
            OcrLine("$168/h", 350, 40),
            OcrLine("$8.39/km", 390, 40),
            OcrLine("18 min · 6.0 km · tarifa 50.34", 430, 22),
            OcrLine("$14.19 de tarifa base dinámica", 520, 24),
            OcrLine("6min (1.7km)", 700, 28),
            OcrLine("12min (4.3km)", 800, 28)
        )

        val offer = assertNotNull(OfferParser.parse(lines))
        assertEquals(50.34, offer.fare, 0.001)
        assertEquals(18, offer.totalMinutes)
        assertEquals(6.0, offer.totalKilometers, 0.001)
    }

    @Test
    fun parsesRealFullScreenExamplesFromV3Failures() {
        val cases = listOf(
            Case("$50.34\n$14.19 de tarifa base dinámica\n6min (1.7km)\n12min (4.3km)", 50.34, 18, 6.0),
            Case("$30.53\n5min (796m)\n6min (2.2km)", 30.53, 11, 2.996),
            Case("$71.57\n$3.23 de tarifa base dinámica\n8min (2.1km)\n17min (8km)", 71.57, 25, 10.1),
            Case("$65.29\n8min (1.8km)\n20min (10km)", 65.29, 28, 11.8),
            Case("$28.93\n11min (4.5km)\n5min (1.6km)", 28.93, 16, 6.1),
            Case("$30.63\n$7.74 de tarifa base dinámica\n3min (394m)\n8min (3.1km)", 30.63, 11, 3.494),
            Case("$176.17\n$17.00 adicionales\n$10.15 de tarifa base dinámica\n6min (874m)\n34min (19.7km)", 176.17, 40, 20.574)
        )

        cases.forEach { case ->
            val offer = assertNotNull(OfferParser.parseSimpleText(case.raw))
            assertEquals(case.fare, offer.fare, 0.001)
            assertEquals(case.minutes, offer.totalMinutes)
            assertEquals(case.km, offer.totalKilometers, 0.001)
        }
    }

    private data class Case(
        val raw: String,
        val fare: Double,
        val minutes: Int,
        val km: Double
    )
}
