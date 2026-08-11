package com.jonathan.didiprofit

import android.content.Context

data class Thresholds(
    val hourlyExcellent: Double = 300.0,
    val hourlyGood: Double = 220.0,
    val kmExcellent: Double = 8.0,
    val kmGood: Double = 6.0,
    val targetHourly1: Double = 150.0,
    val targetHourly2: Double = 180.0,
    val targetHourly3: Double = 210.0
)

class ProfitPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("profit_settings", Context.MODE_PRIVATE)

    fun load(): Thresholds = Thresholds(
        hourlyExcellent = prefs.getFloat("hourly_excellent", 300f).toDouble(),
        hourlyGood = prefs.getFloat("hourly_good", 220f).toDouble(),
        kmExcellent = prefs.getFloat("km_excellent", 8f).toDouble(),
        kmGood = prefs.getFloat("km_good", 6f).toDouble(),
        targetHourly1 = prefs.getFloat("target_hourly_1", 150f).toDouble(),
        targetHourly2 = prefs.getFloat("target_hourly_2", 180f).toDouble(),
        targetHourly3 = prefs.getFloat("target_hourly_3", 210f).toDouble()
    )

    fun save(t: Thresholds) {
        prefs.edit()
            .putFloat("hourly_excellent", t.hourlyExcellent.toFloat())
            .putFloat("hourly_good", t.hourlyGood.toFloat())
            .putFloat("km_excellent", t.kmExcellent.toFloat())
            .putFloat("km_good", t.kmGood.toFloat())
            .putFloat("target_hourly_1", t.targetHourly1.toFloat())
            .putFloat("target_hourly_2", t.targetHourly2.toFloat())
            .putFloat("target_hourly_3", t.targetHourly3.toFloat())
            .apply()
    }
}
