package com.jonathan.didiprofit

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : Activity() {
    private lateinit var repository: HistoryRepository
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HistoryRepository(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()

        content.addView(TextView(this).apply {
            text = "Historial y estadísticas"
            textSize = 27f
            setTextColor(Color.rgb(25, 25, 25))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        content.addView(TextView(this).apply {
            text = "V3 guarda únicamente los datos numéricos de las propuestas. No guarda capturas."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(14))
        })

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        addStatsBlock("Hoy", repository.statsSince(startOfToday))
        addStatsBlock("Todo el historial", repository.statsSince(0L))

        content.addView(Button(this).apply {
            text = "Borrar historial"
            setOnClickListener { confirmClear() }
        }, fullWidth())

        content.addView(TextView(this).apply {
            text = "Propuestas recientes"
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(8))
        })

        val recent = repository.recent(75)
        if (recent.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Todavía no hay propuestas guardadas."
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(12), 0, dp(12))
            })
            return
        }

        val formatter = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())

        recent.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(245, 245, 245))
            }

            card.addView(TextView(this).apply {
                text = String.format(
                    Locale.US,
                    "$%.2f  ·  $%.0f/h  ·  $%.2f/km",
                    item.fare,
                    item.pesosPerHour,
                    item.pesosPerKm
                )
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(20, 20, 20))
            })

            card.addView(TextView(this).apply {
                text = String.format(
                    Locale.US,
                    "%d min · %.1f km · puntaje %d/100",
                    item.totalMinutes,
                    item.totalKm,
                    item.score
                )
                textSize = 14f
                setTextColor(Color.DKGRAY)
            })

            card.addView(TextView(this).apply {
                val seen = if (item.seenCount > 1) " · vista ${item.seenCount} veces" else ""
                text = "${formatter.format(Date(item.lastSeen))}$seen"
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, dp(3), 0, 0)
            })

            content.addView(card, fullWidth().apply {
                setMargins(0, 0, 0, dp(8))
            })
        }
    }

    private fun addStatsBlock(title: String, stats: HistoryStats) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(5))
        })

        val summary = if (stats.count == 0) {
            "Sin propuestas"
        } else {
            String.format(
                Locale.US,
                "%d propuestas únicas\nPromedio: $%.0f/h · $%.2f/km · %.0f/100\nMejor: $%.0f/h · $%.2f/km",
                stats.count,
                stats.avgHourly,
                stats.avgKm,
                stats.avgScore,
                stats.bestHourly,
                stats.bestKm
            )
        }

        content.addView(TextView(this).apply {
            text = summary
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(242, 242, 242))
        }, fullWidth())
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage("Se eliminarán todas las propuestas y estadísticas guardadas en este teléfono.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar") { _, _ ->
                repository.clearAll()
                render()
            }
            .show()
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
