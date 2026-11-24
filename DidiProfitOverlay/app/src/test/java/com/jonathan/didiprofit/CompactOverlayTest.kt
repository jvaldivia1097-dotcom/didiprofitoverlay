package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CompactOverlayTest {
    private val offer = RideOffer(46.31, RouteMetric(9, 2.4), RouteMetric(12, 4.6))
    private val thresholds = Thresholds(
        hourlyExcellent = 130.0, hourlyGood = 100.0,
        kmExcellent = 8.0, kmGood = 6.0, targetHourly1 = 180.0
    )

    @Test
    fun formatsApprovedExampleUsingExistingCalculations() {
        val panel = CompactOffer.from(offer, thresholds)

        assertEquals("$132/h", panel.hourly)
        assertEquals("$6.62/km", panel.distance)
        assertEquals("21 min · 7.0 km · Tarifa $46.31", panel.details)
        assertEquals("Pon Tu Precio · 180/h", panel.target)
        assertEquals("Mín. $63.00   +$16.69", panel.minimum)
        assertEquals(ProfitGrade.EXCELLENT, panel.hourlyGrade)
        assertEquals(ProfitGrade.GOOD, panel.distanceGrade)
    }

    @Test
    fun repeatedOffersAreIdenticalButNewOffersAndThresholdsUpdatePresentation() {
        val panel = CompactOffer.from(offer, thresholds)

        assertEquals(panel, CompactOffer.from(offer.copy(), thresholds.copy()))
        assertNotEquals(panel, CompactOffer.from(offer.copy(fare = 50.0), thresholds))

        val updated = CompactOffer.from(offer, thresholds.copy(
            targetHourly1 = 120.0, kmGood = 7.0
        ))
        assertEquals("Pon Tu Precio · 120/h", updated.target)
        assertEquals("Mín. $42.00   ✓ actual", updated.minimum)
        assertEquals(ProfitGrade.LOW, updated.distanceGrade)
    }

    @Test
    fun storedWaitingFractionsRestoreAndScaleToNewTravelLimits() {
        val position = WaitingPosition.fromPixels(250, 700, 300, 800)
        val restored = WaitingPosition(position.x, position.y)

        assertEquals(250 to 700, restored.resolve(300, 800))
        assertEquals(500 to 350, restored.resolve(600, 400))
        assertEquals(0 to 0, restored.resolve(0, -10))
        assertEquals(0 to 400, WaitingPosition.fromPixels(-1, 900, 300, 800).resolve(600, 400))
    }

    @Test
    fun compactRowsCannotBecomeOwnOfferOrFareOfAnotherOffer() {
        val panel = CompactOffer.from(offer, thresholds)
        val ownRows = listOf(panel.hourly, panel.distance, panel.details, panel.target, panel.minimum)
        val wrapped = listOf(
            panel.hourly, panel.distance, "21 min · 7.0 km ·", "Tarifa $46.31",
            panel.target, "Min. $63.00", "+$16.69"
        )
        val realOffer = listOf("$81.20", "7 min (2.1 km)", "18 min (9.4 km)")
        val expected = RideOffer(81.20, RouteMetric(7, 2.1), RouteMetric(18, 9.4))

        for (self in listOf(ownRows, wrapped, wrapped.dropLast(2) + "Mín. $42.00   ✓ actual")) {
            assertNull(OfferParser.parseSimpleText(self.joinToString("\n")))
            assertEquals(expected, OfferParser.parseSimpleText((self + realOffer).joinToString("\n")))
            val lines = (self + realOffer).mapIndexed { i, text -> OcrLine(text, i * 30, 20) }
            assertEquals(expected, OfferParser.parse(lines))
            assertNull(OfferParser.parseSimpleText((self + realOffer.drop(1)).joinToString("\n")))
            assertNull(OfferParser.parse(lines.filterNot { it.text == "$81.20" }))
        }
    }
}
