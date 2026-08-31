package com.jonathan.didiprofit

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
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

    private var selectedDayStart: Long = 0L
    private var showAllHistory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HistoryRepository(this)
        selectedDayStart = startOfToday()

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
        AppUi.applyTopSystemInset(root)

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
        root.addView(
            AppUi.bottomNav(
                this,
                AppSection.HISTORY
            )
        )
        return root
    }

    private fun render() {
        content.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(AppUi.TEXT)
            setPadding(
                0,
                0,
                AppUi.dp(this@HistoryActivity, 12),
                0
            )
            setOnClickListener { finish() }
        })

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titles.addView(
            AppUi.title(
                this,
                "Historial y estadísticas",
                24f
            )
        )
        titles.addView(
            AppUi.body(
                this,
                "Filtra tus propuestas por día",
                12f
            )
        )
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
                startActivity(
                    Intent(
                        this@HistoryActivity,
                        TopTenActivity::class.java
                    ).putExtra(
                        TopTenActivity.EXTRA_DAY_START,
                        if (showAllHistory) startOfToday()
                        else selectedDayStart
                    )
                )
            }
        }
        header.addView(topShortcut)
        content.addView(header)

        content.addView(
            dateFilterCard(),
            AppUi.fullWidth(this, 18, 0)
        )

        val stats = if (showAllHistory) {
            repository.statsSince(0L)
        } else {
            repository.statsBetween(
                selectedDayStart,
                nextDayStart(selectedDayStart)
            )
        }

        content.addView(
            AppUi.sectionTitle(
                this,
                if (showAllHistory) {
                    "Resumen general"
                } else {
                    "Resumen del día"
                }
            )
        )
        content.addView(statsCard(stats))

        content.addView(
            AppUi.secondaryButton(
                this,
                "★  Ver Top 10 de ${shortDateLabel(selectedDayStart)}"
            ) {
                startActivity(
                    Intent(
                        this,
                        TopTenActivity::class.java
                    ).putExtra(
                        TopTenActivity.EXTRA_DAY_START,
                        selectedDayStart
                    )
                )
            },
            AppUi.fullWidth(this, 12, 0)
        )

        content.addView(
            AppUi.sectionTitle(
                this,
                if (showAllHistory) {
                    "Todas las propuestas"
                } else {
                    "Propuestas del día"
                }
            )
        )

        val entries = if (showAllHistory) {
            repository.recent(300)
        } else {
            repository.recentBetween(
                selectedDayStart,
                nextDayStart(selectedDayStart),
                300
            )
        }

        if (entries.isEmpty()) {
            val empty = AppUi.card(this)
            empty.addView(
                AppUi.title(
                    this,
                    if (showAllHistory) {
                        "Todavía no hay propuestas"
                    } else {
                        "No hay propuestas en esta fecha"
                    },
                    16f
                )
            )
            empty.addView(
                AppUi.body(
                    this,
                    if (showAllHistory) {
                        "Las propuestas confirmadas aparecerán aquí."
                    } else {
                        "Prueba seleccionando otro día en el calendario."
                    },
                    13f
                ).apply {
                    setPadding(
                        0,
                        AppUi.dp(this@HistoryActivity, 5),
                        0,
                        0
                    )
                }
            )
            content.addView(empty)
        } else {
            val formatter = SimpleDateFormat(
                if (showAllHistory) {
                    "dd/MM/yyyy · HH:mm"
                } else {
                    "HH:mm"
                },
                Locale.getDefault()
            )

            entries.forEach { item ->
                content.addView(
                    proposalCard(
                        item,
                        formatter
                    ),
                    AppUi.fullWidth(this, 0, 8)
                )
            }
        }

        content.addView(
            AppUi.body(
                this,
                "Mantén presionada una propuesta para eliminar un registro erróneo.",
                11.5f
            ).apply {
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    AppUi.dp(this@HistoryActivity, 12),
                    0,
                    0
                )
            }
        )

        content.addView(
            AppUi.dangerButton(
                this,
                "Borrar todo el historial"
            ) {
                confirmClear()
            },
            AppUi.fullWidth(this, 16, 0)
        )

        content.addView(
            AppUi.body(
                this,
                "V3.5.1 · El historial guarda propuestas sólo después de 3 lecturas estables.",
                11f
            ).apply {
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    AppUi.dp(this@HistoryActivity, 12),
                    0,
                    0
                )
            }
        )
    }

    private fun dateFilterCard(): LinearLayout {
        val card = AppUi.card(this)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        tabs.addView(
            AppUi.segment(
                this,
                "Por día",
                !showAllHistory
            ) {
                showAllHistory = false
                render()
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(
                    0,
                    0,
                    AppUi.dp(this@HistoryActivity, 4),
                    0
                )
            }
        )

        tabs.addView(
            AppUi.segment(
                this,
                "Todo",
                showAllHistory
            ) {
                showAllHistory = true
                render()
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(
                    AppUi.dp(this@HistoryActivity, 4),
                    0,
                    0,
                    0
                )
            }
        )

        card.addView(tabs)

        if (!showAllHistory) {
            val dateRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    0,
                    AppUi.dp(this@HistoryActivity, 13),
                    0,
                    0
                )
            }

            dateRow.addView(
                dateArrow("‹") {
                    selectedDayStart =
                        previousDayStart(selectedDayStart)
                    render()
                }
            )

            val dateCenter = TextView(this).apply {
                text = longDateLabel(selectedDayStart)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(AppUi.TEXT)
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
                setPadding(
                    AppUi.dp(this@HistoryActivity, 8),
                    AppUi.dp(this@HistoryActivity, 9),
                    AppUi.dp(this@HistoryActivity, 8),
                    AppUi.dp(this@HistoryActivity, 9)
                )
                background = AppUi.rounded(
                    this@HistoryActivity,
                    AppUi.BG,
                    13,
                    AppUi.BORDER
                )
                setOnClickListener {
                    openDatePicker()
                }
            }
            dateRow.addView(
                dateCenter,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        AppUi.dp(this@HistoryActivity, 7),
                        0,
                        AppUi.dp(this@HistoryActivity, 7),
                        0
                    )
                }
            )

            dateRow.addView(
                dateArrow("›") {
                    val candidate =
                        nextDayStart(selectedDayStart)
                    if (candidate <= startOfToday()) {
                        selectedDayStart = candidate
                        render()
                    }
                }
            )

            card.addView(dateRow)

            card.addView(
                AppUi.body(
                    this,
                    "Toca la fecha para abrir el calendario",
                    11f
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        0,
                        AppUi.dp(this@HistoryActivity, 7),
                        0,
                        0
                    )
                }
            )
        } else {
            card.addView(
                AppUi.body(
                    this,
                    "Mostrando todo el historial guardado",
                    12.5f
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        0,
                        AppUi.dp(this@HistoryActivity, 12),
                        0,
                        0
                    )
                }
            )
        }

        return card
    }

    private fun dateArrow(
        symbol: String,
        action: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = symbol
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(AppUi.ORANGE)
            background = AppUi.rounded(
                this@HistoryActivity,
                AppUi.ORANGE_TINT,
                13,
                AppUi.ORANGE
            )
            setPadding(
                AppUi.dp(this@HistoryActivity, 11),
                AppUi.dp(this@HistoryActivity, 3),
                AppUi.dp(this@HistoryActivity, 11),
                AppUi.dp(this@HistoryActivity, 3)
            )
            setOnClickListener { action() }
        }

    private fun statsCard(
        stats: HistoryStats
    ): LinearLayout {
        val card = AppUi.card(this)

        if (stats.count == 0) {
            card.addView(
                AppUi.title(
                    this,
                    "Sin datos para este filtro",
                    16f
                )
            )
            card.addView(
                AppUi.body(
                    this,
                    "No hay propuestas confirmadas en el periodo seleccionado.",
                    13f
                ).apply {
                    setPadding(
                        0,
                        AppUi.dp(this@HistoryActivity, 5),
                        0,
                        0
                    )
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
                String.format(
                    Locale.US,
                    "$%.0f/h",
                    stats.avgHourly
                ),
                "Promedio $/h",
                AppUi.GREEN
            ),
            Triple(
                String.format(
                    Locale.US,
                    "%.0f/100",
                    stats.avgScore
                ),
                "Puntaje",
                AppUi.scoreColor(
                    stats.avgScore.toInt()
                )
            )
        )

        metrics.forEach {
            (value, label, color) ->

            row.addView(
                AppUi.metricTile(
                    this,
                    value,
                    label,
                    color
                ),
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
        val card = AppUi.card(
            this,
            Color.WHITE,
            13
        )

        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        firstRow.addView(
            AppUi.title(
                this,
                String.format(
                    Locale.US,
                    "$%.2f",
                    item.fare
                ),
                17f
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        firstRow.addView(
            TextView(this).apply {
                text = String.format(
                    Locale.US,
                    "$%.0f/h",
                    item.pesosPerHour
                )
                textSize = 17f
                setTextColor(
                    AppUi.scoreColor(item.score)
                )
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
            }
        )

        firstRow.addView(
            TextView(this).apply {
                text = item.score.toString()
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(
                    AppUi.scoreColor(item.score)
                )
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
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    AppUi.dp(this@HistoryActivity, 10),
                    0,
                    0,
                    0
                )
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
                setPadding(
                    0,
                    AppUi.dp(this@HistoryActivity, 5),
                    0,
                    0
                )
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
                "${formatter.format(Date(item.firstSeen))}$seen",
                11.5f
            ).apply {
                setPadding(
                    0,
                    AppUi.dp(this@HistoryActivity, 4),
                    0,
                    0
                )
            }
        )

        card.setOnLongClickListener {
            confirmDelete(item)
            true
        }

        return card
    }

    private fun confirmDelete(
        item: HistoryEntry
    ) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar propuesta")
            .setMessage(
                String.format(
                    Locale.US,
                    "¿Eliminar la propuesta de $%.2f ($%.0f/h, $%.2f/km)?",
                    item.fare,
                    item.pesosPerHour,
                    item.pesosPerKm
                )
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") {
                    _,
                    _ ->

                repository.deleteById(item.id)
                render()
            }
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Borrar todo el historial")
            .setMessage(
                "Se eliminarán todas las propuestas y estadísticas guardadas en este teléfono."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar") {
                    _,
                    _ ->

                repository.clearAll()
                render()
            }
            .show()
    }

    private fun openDatePicker() {
        val c = Calendar.getInstance().apply {
            timeInMillis = selectedDayStart
        }

        DatePickerDialog(
            this,
            {
                    _,
                    year,
                    month,
                    dayOfMonth ->

                selectedDayStart =
                    startOfDay(
                        year,
                        month,
                        dayOfMonth
                    )
                showAllHistory = false
                render()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate =
                System.currentTimeMillis()
        }.show()
    }

    private fun longDateLabel(
        millis: Long
    ): String {
        val prefix =
            if (millis == startOfToday()) {
                "Hoy · "
            } else {
                ""
            }

        val format = SimpleDateFormat(
            "EEE d 'de' MMM yyyy",
            Locale("es", "MX")
        )

        return prefix +
            format.format(Date(millis))
                .replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(
                            Locale("es", "MX")
                        )
                    } else {
                        it.toString()
                    }
                }
    }

    private fun shortDateLabel(
        millis: Long
    ): String =
        if (millis == startOfToday()) {
            "hoy"
        } else {
            SimpleDateFormat(
                "d MMM",
                Locale("es", "MX")
            ).format(Date(millis))
        }

    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        return startOfDay(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun startOfDay(
        year: Int,
        month: Int,
        day: Int
    ): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun previousDayStart(
        millis: Long
    ): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis

    private fun nextDayStart(
        millis: Long
    ): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
}
