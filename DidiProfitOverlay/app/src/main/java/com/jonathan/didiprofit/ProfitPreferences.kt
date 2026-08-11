package com.jonathan.didiprofit

import android.content.Context

data class Thresholds(
    val hourlyExcellent: Double = 300.0,
    val hourlyGood: Double = 220.0,
    val kmExcellent: Double = 8.0,
    val kmGood: Double = 6.0
)

class ProfitPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("profit_settings", Context.MODE_PRIVATE)

    fun load(): Thresholds = Thresholds(
        hourlyExcellent = prefs.getFloat("hourly_excellent", 300f).toDouble(),
        hourlyGood = prefs.getFloat("hourly_good", 220f).toDouble(),
        kmExcellent = prefs.getFloat("km_excellent", 8f).toDouble(),
        kmGood = prefs.getFloat("km_good", 6f).toDouble()
    )

    fun save(t: Thresholds) {
        prefs.edit()
            .putFloat("hourly_excellent", t.hourlyExcellent.toFloat())
            .putFloat("hourly_good", t.hourlyGood.toFloat())
            .putFloat("km_excellent", t.kmExcellent.toFloat())
            .putFloat("km_good", t.kmGood.toFloat())
            .apply()
    }
}
