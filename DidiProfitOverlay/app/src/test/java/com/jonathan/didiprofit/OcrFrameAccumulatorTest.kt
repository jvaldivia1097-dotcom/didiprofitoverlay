package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrFrameAccumulatorTest {

    @Test
    fun compatibilityShimDoesNotMergeFrames() {
        val accumulator = OcrFrameAccumulator()

        val first = listOf(OcrLine("$84.38", centerY = 650, height = 80))
        val second = listOf(OcrLine("22min (12.2km)", centerY = 1130, height = 31))

        accumulator.add(first, now = 1000L)
        val output = accumulator.add(second, now = 1200L)

        assertEquals(second, output)
    }
}
