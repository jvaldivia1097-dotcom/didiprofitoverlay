package com.jonathan.didiprofit

import kotlin.math.abs

/**
 * V3.5.1
 *
 * This tracker does NOT combine OCR frames and does NOT alter what the overlay shows.
 * It only decides when a parsed proposal has been stable long enough to be persisted
 * in history.
 */
class HistoryCandidateTracker(
    private val requiredStableReads: Int = 3
) {
    private var candidate: RideOffer? = null
    private var stableReads: Int = 0
    private var alreadyConfirmed: Boolean = false

    fun observe(offer: RideOffer): RideOffer? {
        val previous = candidate

        if (previous != null && sameStableProposal(previous, offer)) {
            candidate = offer
            stableReads += 1
        } else {
            candidate = offer
            stableReads = 1
            alreadyConfirmed = false
        }

        if (stableReads >= requiredStableReads && !alreadyConfirmed) {
            alreadyConfirmed = true
            return candidate
        }

        return null
    }

    fun reset() {
        candidate = null
        stableReads = 0
        alreadyConfirmed = false
    }

    private fun sameStableProposal(a: RideOffer, b: RideOffer): Boolean =
        abs(a.fare - b.fare) <= FARE_TOLERANCE &&
            a.pickup.minutes == b.pickup.minutes &&
            a.trip.minutes == b.trip.minutes &&
            abs(a.pickup.kilometers - b.pickup.kilometers) <= PICKUP_KM_TOLERANCE &&
            abs(a.trip.kilometers - b.trip.kilometers) <= TRIP_KM_TOLERANCE

    companion object {
        private const val FARE_TOLERANCE = 0.02
        private const val PICKUP_KM_TOLERANCE = 0.12
        private const val TRIP_KM_TOLERANCE = 0.12
    }
}
