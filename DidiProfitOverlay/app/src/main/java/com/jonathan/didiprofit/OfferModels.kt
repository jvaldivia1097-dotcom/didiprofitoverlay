package com.jonathan.didiprofit

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
}

data class OcrLine(
    val text: String,
    val centerY: Int = 0,
    val height: Int = 0
)
