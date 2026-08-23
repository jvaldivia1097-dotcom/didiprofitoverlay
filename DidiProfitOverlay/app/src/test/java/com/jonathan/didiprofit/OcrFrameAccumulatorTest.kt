package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OcrFrameAccumulatorTest {

    @Test
    fun combinesConsecutiveFramesForSameFare() {
        val accumulator = OcrFrameAccumulator()

        accumulator.add(
            listOf(
                OcrLine("$84.38", 650, 80),
                OcrLine("5min", 1000, 31)
            ),
            now = 1000L
        )

        val merged = accumulator.add(
            listOf(
                OcrLine("$84.38", 650, 80),
                OcrLine("(699m)", 1001, 31),
                OcrLine("22min (12.2km)", 1130, 31)
            ),
            now = 1250L
        )

        val offer = assertNotNull(OfferParser.parse(merged))
        assertEquals(84.38, offer.fare, 0.001)
        assertEquals(27, offer.totalMinutes)
        assertEquals(12.899, offer.totalKilometers, 0.001)
    }

    @Test
    fun newFareResetsOldProposalFrames() {
        val accumulator = OcrFrameAccumulator()

        accumulator.add(
            listOf(
                OcrLine("$84.38", 650, 80),
                OcrLine("5min (699m)", 1000, 31)
            ),
            now = 1000L
        )

        val merged = accumulator.add(
            listOf(
                OcrLine("$39.27", 650, 80),
                OcrLine("7min (1.5km)", 1000, 31),
                OcrLine("9min (3.5km)", 1130, 31)
            ),
            now = 1200L
        )

        val offer = assertNotNull(OfferParser.parse(merged))
        assertEquals(39.27, offer.fare, 0.001)
    }
}
