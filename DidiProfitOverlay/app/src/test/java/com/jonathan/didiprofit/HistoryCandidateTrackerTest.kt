package com.jonathan.didiprofit

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HistoryCandidateTrackerTest {

    private fun offer(
        fare: Double = 64.78,
        pickupMin: Int = 5,
        pickupKm: Double = 1.2,
        tripMin: Int = 7,
        tripKm: Double = 3.3
    ) = RideOffer(
        fare = fare,
        pickup = RouteMetric(pickupMin, pickupKm),
        trip = RouteMetric(tripMin, tripKm)
    )

    @Test
    fun requiresThreeStableReads() {
        val tracker = HistoryCandidateTracker()

        assertNull(tracker.observe(offer()))
        assertNull(tracker.observe(offer()))
        assertNotNull(tracker.observe(offer()))
    }

    @Test
    fun transientWrongReadDoesNotGetConfirmed() {
        val tracker = HistoryCandidateTracker()

        val wrong = offer(
            fare = 529.99,
            pickupMin = 1,
            pickupKm = 0.2,
            tripMin = 16,
            tripKm = 2.2
        )
        val correct = offer()

        assertNull(tracker.observe(wrong))
        assertNull(tracker.observe(correct))
        assertNull(tracker.observe(correct))
        assertNotNull(tracker.observe(correct))
    }

    @Test
    fun aChangedProposalRestartsConfirmation() {
        val tracker = HistoryCandidateTracker()

        assertNull(tracker.observe(offer()))
        assertNull(tracker.observe(offer()))
        assertNull(tracker.observe(offer(fare = 82.09)))
        assertNull(tracker.observe(offer(fare = 82.09)))
        assertNotNull(tracker.observe(offer(fare = 82.09)))
    }

    @Test
    fun resetBreaksTheSequence() {
        val tracker = HistoryCandidateTracker()

        tracker.observe(offer())
        tracker.observe(offer())
        tracker.reset()

        assertNull(tracker.observe(offer()))
        assertNull(tracker.observe(offer()))
        assertNotNull(tracker.observe(offer()))
    }
}
