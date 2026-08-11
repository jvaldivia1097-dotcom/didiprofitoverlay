package com.jonathan.didiprofit

import kotlin.math.ceil

data class RouteMetric(
    val minutes: Int,
    val kilometers: Double
)

data class RideOffer(
    val fare: Double,
    val pickup: RouteMetric,
    val trip: RouteMetric
) {
    val totalMinutes: Int get() = pickup.minutes + trip.minutes
    val totalKilometers: Double get() = pickup.kilometers + trip.kilometers
    val pesosPerHour: Double
        get() = if (totalMinutes > 0) fare * 60.0 / totalMinutes else 0.0
    val pesosPerKm: Double
        get() = if (totalKilometers > 0.0) fare / totalKilometers else 0.0

    /** Minimum fare, rounded UP to cents, needed to reach at least the target hourly rate. */
    fun minimumFareForHourly(targetHourly: Double): Double {
        if (targetHourly <= 0.0 || totalMinutes <= 0) return 0.0
        val raw = targetHourly * totalMinutes / 60.0
        return ceil((raw - 1e-9) * 100.0) / 100.0
    }
}

data class OcrLine(
    val text: String,
    val centerY: Int = 0,
    val height: Int = 0
)
