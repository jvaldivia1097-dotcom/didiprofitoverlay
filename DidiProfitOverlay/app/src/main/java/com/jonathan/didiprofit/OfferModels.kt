package com.jonathan.didiprofit

import kotlin.math.ceil
import kotlin.math.roundToInt

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

    fun minimumFareForHourly(targetHourly: Double): Double {
        if (targetHourly <= 0.0 || totalMinutes <= 0) return 0.0
        val raw = targetHourly * totalMinutes / 60.0
        return ceil((raw - 1e-9) * 100.0) / 100.0
    }

    /**
     * V3 score (0..100).
     * 65% hourly profitability + 35% per-km profitability.
     * "Good" = 50 points in each component, "Excellent" = 100.
     */
    fun profitabilityScore(t: Thresholds): Int {
        fun component(value: Double, good: Double, excellent: Double): Double {
            if (value <= 0.0) return 0.0
            if (excellent <= good) {
                if (excellent <= 0.0) return 0.0
                return (100.0 * value / excellent).coerceIn(0.0, 100.0)
            }
            return when {
                value >= excellent -> 100.0
                value >= good -> 50.0 + 50.0 * (value - good) / (excellent - good)
                good > 0.0 -> 50.0 * value / good
                else -> 0.0
            }.coerceIn(0.0, 100.0)
        }

        val hourly = component(pesosPerHour, t.hourlyGood, t.hourlyExcellent)
        val km = component(pesosPerKm, t.kmGood, t.kmExcellent)
        return (hourly * 0.65 + km * 0.35).roundToInt().coerceIn(0, 100)
    }

    fun profitabilityLabel(t: Thresholds): String = when (profitabilityScore(t)) {
        in 85..100 -> "Excelente"
        in 70..84 -> "Muy buena"
        in 55..69 -> "Buena"
        in 40..54 -> "Regular"
        else -> "Baja"
    }
}

data class OcrLine(
    val text: String,
    val centerY: Int = 0,
    val height: Int = 0
)
