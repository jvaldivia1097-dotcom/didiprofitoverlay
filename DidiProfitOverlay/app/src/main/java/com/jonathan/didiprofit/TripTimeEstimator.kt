package com.jonathan.didiprofit

object TripTimeEstimator {
    fun estimatedFinishMillis(
        totalMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (totalMinutes <= 0 || totalMinutes > 360) return null
        return nowMillis + totalMinutes * 60_000L
    }

    fun estimatedFinishMillis(
        offer: RideOffer,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? = estimatedFinishMillis(offer.totalMinutes, nowMillis)
}
