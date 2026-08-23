package com.jonathan.didiprofit

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
    private var topMode = HistoryTopMode.SCORE

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
            text = "V3.3 guarda únicamente los datos numéricos de las propuestas. No guarda capturas."
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

        val todayStats = repository.statsSince(startOfToday)
        addStatsBlock("Hoy", todayStats)
        addTopTenBlock(startOfToday, todayStats)
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

        val recent = repository.recent(100)
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
            addProposalCard(item, formatter, rank = null)
        }
    }

    private fun addTopTenBlock(startOfToday: Long, todayStats: HistoryStats) {
        content.addView(TextView(this).apply {
            text = "Mejores propuestas de hoy"
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(8))
        })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun modeButton(label: String, mode: HistoryTopMode): Button =
            Button(this).apply {
                text = label
                isEnabled = topMode != mode
                textSize = 11f
                setOnClickListener {
                    topMode = mode
                    render()
                }
            }

        buttonRow.addView(
            modeButton("Rentabilidad", HistoryTopMode.SCORE),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        buttonRow.addView(
            modeButton("$/hora", HistoryTopMode.HOURLY),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        buttonRow.addView(
            modeButton("$/km", HistoryTopMode.KM),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        content.addView(buttonRow, fullWidth())

        val top = repository.topSince(startOfToday, topMode, 10)
        if (top.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Todavía no hay suficientes datos de hoy."
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(242, 242, 242))
            }, fullWidth())
            return
        }

        val avgHourly = top.map { it.pesosPerHour }.average()
        val avgKm = top.map { it.pesosPerKm }.average()
        val avgScore = top.map { it.score }.average()
        val cutoff = top.last()

        val cutoffText = when (topMode) {
            HistoryTopMode.SCORE -> String.format(
                Locale.US,
                "%d/100 · $%.0f/h · $%.2f/km",
                cutoff.score,
                cutoff.pesosPerHour,
                cutoff.pesosPerKm
            )
            HistoryTopMode.HOURLY -> String.format(Locale.US, "$%.0f/h", cutoff.pesosPerHour)
            HistoryTopMode.KM -> String.format(Locale.US, "$%.2f/km", cutoff.pesosPerKm)
        }

        val n = top.size
        val summary = String.format(
            Locale.US,
            "Promedio Top %d: $%.0f/h · $%.2f/km · %.0f/100\n" +
                "Máximos observados hoy: $%.0f/h · $%.2f/km\n" +
                "Corte para entrar al Top %d: %s",
            n,
            avgHourly,
            avgKm,
            avgScore,
            todayStats.bestHourly,
            todayStats.bestKm,
            n,
            cutoffText
        )

        content.addView(TextView(this).apply {
            text = summary
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(242, 242, 242))
        }, fullWidth().apply {
            setMargins(0, dp(5), 0, dp(8))
        })

        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        top.forEachIndexed { index, item ->
            addProposalCard(item, formatter, rank = index + 1)
        }
    }

    private fun addProposalCard(
        item: HistoryEntry,
        formatter: SimpleDateFormat,
        rank: Int?
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        card.addView(TextView(this).apply {
            val prefix = rank?.let { "#$it  " } ?: ""
            text = prefix + String.format(
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
