package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OfferParserV33Test {

    @Test
    fun standardFullscreenOfferStillParses() {
        val raw = """
            $42.88
            5.00 · 18 viajes
            7min (1.6km)
            7min (4.6km)
        """.trimIndent()

        val offer = assertNotNull(OfferParser.parseSimpleText(raw))
        assertEquals(42.88, offer.fare, 0.001)
        assertEquals(14, offer.totalMinutes)
        assertEquals(6.2, offer.totalKilometers, 0.001)
    }

    @Test
    fun splitTransientRowsAreNotCombinedAnymore() {
        val lines = listOf(
            OcrLine("$39.27", centerY = 690, height = 80),
            OcrLine("7min", centerY = 955, height = 31),
            OcrLine("(1.5km)", centerY = 957, height = 31),
            OcrLine("9min", centerY = 1090, height = 31),
            OcrLine("(3.5km)", centerY = 1092, height = 31)
        )

        // Deliberate V3.3.2 rollback: do not invent a calculation from fragments
        // while a proposal/card may still be moving.
        assertNull(OfferParser.parse(lines))
    }
}
