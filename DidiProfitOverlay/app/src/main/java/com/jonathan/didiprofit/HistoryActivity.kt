package com.jonathan.didiprofit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
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

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.BG)
        }

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AppUi.dp(this@HistoryActivity, 18),
                AppUi.dp(this@HistoryActivity, 18),
                AppUi.dp(this@HistoryActivity, 18),
                AppUi.dp(this@HistoryActivity, 28)
            )
        }
        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(AppUi.bottomNav(this, AppSection.HISTORY))
        return root
    }

    private fun render() {
        content.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(AppUi.TEXT)
            setPadding(0, 0, AppUi.dp(this@HistoryActivity, 12), 0)
            setOnClickListener { finish() }
        }
        header.addView(back)

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titles.addView(AppUi.title(this, "Historial y estadísticas", 24f))
        titles.addView(AppUi.body(this, "Datos numéricos · sin capturas", 12f))
        header.addView(
            titles,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val topShortcut = TextView(this).apply {
            text = "★ Top 10"
            textSize = 13f
            setTextColor(AppUi.ORANGE)
            setPadding(
                AppUi.dp(this@HistoryActivity, 10),
                AppUi.dp(this@HistoryActivity, 8),
                AppUi.dp(this@HistoryActivity, 10),
                AppUi.dp(this@HistoryActivity, 8)
            )
            background = AppUi.rounded(
                this@HistoryActivity,
                AppUi.ORANGE_TINT,
                14,
                AppUi.ORANGE
            )
            setOnClickListener {
                startActivity(Intent(this@HistoryActivity, TopTenActivity::class.java))
            }
        }
        header.addView(topShortcut)

        content.addView(header)

        val todayStats = repository.statsSince(startOfToday())
        content.addView(AppUi.sectionTitle(this, "Hoy"))
        content.addView(statsCard(todayStats, today = true))

        val allStats = repository.statsSince(0L)
        content.addView(AppUi.sectionTitle(this, "Todo el historial"))
        content.addView(statsCard(allStats, today = false))

        val topAction = AppUi.secondaryButton(this, "★  Ver mejores propuestas") {
            startActivity(Intent(this, TopTenActivity::class.java))
        }
        content.addView(topAction, AppUi.fullWidth(this, 12, 0))

        content.addView(AppUi.sectionTitle(this, "Propuestas recientes"))

        val recent = repository.recent(100)
        if (recent.isEmpty()) {
            val empty = AppUi.card(this)
            empty.addView(AppUi.title(this, "Todavía no hay propuestas", 16f))
            empty.addView(
                AppUi.body(
                    this,
                    "Cuando el análisis detecte propuestas, aparecerán aquí automáticamente.",
                    13f
                ).apply {
                    setPadding(0, AppUi.dp(this@HistoryActivity, 5), 0, 0)
                }
            )
            content.addView(empty)
        } else {
            val formatter = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
            recent.forEach { item ->
                content.addView(
                    proposalCard(item, formatter),
                    AppUi.fullWidth(this, 0, 8)
                )
            }
        }

        content.addView(
            AppUi.dangerButton(this, "Borrar historial") {
                confirmClear()
            },
            AppUi.fullWidth(this, 18, 0)
        )

        content.addView(
            AppUi.body(
                this,
                "V3.4 conserva la base de datos e historial de V3.3.2.",
                11.5f
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, AppUi.dp(this@HistoryActivity, 12), 0, 0)
            }
        )
    }

    private fun statsCard(
        stats: HistoryStats,
        today: Boolean
    ): LinearLayout {
        val card = AppUi.card(this)

        if (stats.count == 0) {
            card.addView(
                AppUi.title(
                    this,
                    if (today) "Sin propuestas hoy" else "Sin historial",
                    16f
                )
            )
            card.addView(
                AppUi.body(
                    this,
                    if (today) {
                        "Aún no hay suficientes datos del día para mostrar estadísticas."
                    } else {
                        "Las estadísticas aparecerán cuando se guarden propuestas."
                    },
                    13f
                ).apply {
                    setPadding(0, AppUi.dp(this@HistoryActivity, 5), 0, 0)
                }
            )
            return card
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val metrics = listOf(
            Triple(
                stats.count.toString(),
                "Propuestas",
                AppUi.TEXT
            ),
            Triple(
                String.format(Locale.US, "$%.0f/h", stats.avgHourly),
                "Promedio $/h",
                AppUi.GREEN
            ),
            Triple(
                String.format(Locale.US, "%.0f/100", stats.avgScore),
                "Puntaje",
                AppUi.scoreColor(stats.avgScore.toInt())
            )
        )

        metrics.forEach { (value, label, color) ->
            row.addView(
                AppUi.metricTile(this, value, label, color),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        AppUi.dp(this@HistoryActivity, 3),
                        0,
                        AppUi.dp(this@HistoryActivity, 3),
                        0
                    )
                }
            )
        }

        card.addView(row)
        card.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "Promedio $/km: $%.2f   ·   Mejor $%.0f/h   ·   Mejor $%.2f/km",
                    stats.avgKm,
                    stats.bestHourly,
                    stats.bestKm
                ),
                12.5f,
                AppUi.TEXT
            ).apply {
                setPadding(
                    AppUi.dp(this@HistoryActivity, 4),
                    AppUi.dp(this@HistoryActivity, 12),
                    AppUi.dp(this@HistoryActivity, 4),
                    0
                )
            }
        )
        return card
    }

    private fun proposalCard(
        item: HistoryEntry,
        formatter: SimpleDateFormat
    ): LinearLayout {
        val card = AppUi.card(this, Color.WHITE, 13)

        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fare = AppUi.title(
            this,
            String.format(Locale.US, "$%.2f", item.fare),
            17f
        )
        firstRow.addView(
            fare,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        firstRow.addView(
            TextView(this).apply {
                text = String.format(Locale.US, "$%.0f/h", item.pesosPerHour)
                textSize = 17f
                setTextColor(AppUi.scoreColor(item.score))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        )

        val score = TextView(this).apply {
            text = "${item.score}"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(AppUi.scoreColor(item.score))
            background = AppUi.rounded(
                this@HistoryActivity,
                Color.WHITE,
                99,
                AppUi.scoreColor(item.score)
            )
            setPadding(
                AppUi.dp(this@HistoryActivity, 8),
                AppUi.dp(this@HistoryActivity, 4),
                AppUi.dp(this@HistoryActivity, 8),
                AppUi.dp(this@HistoryActivity, 4)
            )
        }
        firstRow.addView(
            score,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(AppUi.dp(this@HistoryActivity, 10), 0, 0, 0)
            }
        )
        card.addView(firstRow)

        card.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "%d min · %.1f km   ·   $%.2f/km",
                    item.totalMinutes,
                    item.totalKm,
                    item.pesosPerKm
                ),
                13f,
                AppUi.TEXT
            ).apply {
                setPadding(0, AppUi.dp(this@HistoryActivity, 5), 0, 0)
            }
        )

        val seen = if (item.seenCount > 1) {
            " · vista ${item.seenCount} veces"
        } else {
            ""
        }
        card.addView(
            AppUi.body(
                this,
                "${formatter.format(Date(item.lastSeen))}$seen",
                11.5f
            ).apply {
                setPadding(0, AppUi.dp(this@HistoryActivity, 4), 0, 0)
            }
        )

        return card
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage(
                "Se eliminarán todas las propuestas y estadísticas guardadas en este teléfono."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar") { _, _ ->
                repository.clearAll()
                render()
            }
            .show()
    }

    private fun startOfToday(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
