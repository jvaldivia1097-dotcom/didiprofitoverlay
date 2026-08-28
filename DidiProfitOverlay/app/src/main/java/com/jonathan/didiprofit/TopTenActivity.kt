package com.jonathan.didiprofit

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
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

class TopTenActivity : Activity() {
    private lateinit var repository: HistoryRepository
    private lateinit var content: LinearLayout

    private var mode = HistoryTopMode.SCORE
    private var selectedDayStart: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HistoryRepository(this)

        selectedDayStart =
            intent.getLongExtra(
                EXTRA_DAY_START,
                startOfToday()
            )

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

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AppUi.dp(this@TopTenActivity, 18),
                AppUi.dp(this@TopTenActivity, 18),
                AppUi.dp(this@TopTenActivity, 18),
                AppUi.dp(this@TopTenActivity, 28)
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
                AppSection.TOP10
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

        header.addView(
            TextView(this).apply {
                text = "‹"
                textSize = 34f
                setTextColor(AppUi.TEXT)
                setPadding(
                    0,
                    0,
                    AppUi.dp(this@TopTenActivity, 12),
                    0
                )
                setOnClickListener {
                    finish()
                }
            }
        )

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titles.addView(
            AppUi.title(
                this,
                "Top 10",
                25f
            )
        )
        titles.addView(
            AppUi.body(
                this,
                "Mejores propuestas por fecha",
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
        content.addView(header)

        content.addView(
            dateSelectorCard(),
            AppUi.fullWidth(this, 18, 0)
        )

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf(
            "Rentabilidad" to HistoryTopMode.SCORE,
            "$/hora" to HistoryTopMode.HOURLY,
            "$/km" to HistoryTopMode.KM
        ).forEach {
            (label, value) ->

            tabs.addView(
                AppUi.segment(
                    this,
                    label,
                    mode == value
                ) {
                    mode = value
                    render()
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        AppUi.dp(this@TopTenActivity, 3),
                        0,
                        AppUi.dp(this@TopTenActivity, 3),
                        0
                    )
                }
            )
        }

        content.addView(
            tabs,
            AppUi.fullWidth(this, 16, 0)
        )

        val end = nextDayStart(selectedDayStart)
        val dayStats =
            repository.statsBetween(
                selectedDayStart,
                end
            )
        val top =
            repository.topBetween(
                selectedDayStart,
                end,
                mode,
                10
            )

        content.addView(
            AppUi.sectionTitle(
                this,
                "Resumen"
            )
        )

        if (top.isEmpty()) {
            val empty = AppUi.card(this)
            empty.addView(
                AppUi.title(
                    this,
                    "No hay Top 10 para esta fecha",
                    16f
                )
            )
            empty.addView(
                AppUi.body(
                    this,
                    "Selecciona otro día para consultar sus mejores propuestas guardadas.",
                    13f
                ).apply {
                    setPadding(
                        0,
                        AppUi.dp(this@TopTenActivity, 5),
                        0,
                        0
                    )
                }
            )
            content.addView(empty)
            return
        }

        val avgHourly =
            top.map {
                it.pesosPerHour
            }.average()

        val avgKm =
            top.map {
                it.pesosPerKm
            }.average()

        val avgScore =
            top.map {
                it.score
            }.average()

        val cutoff = top.last()

        val summaryCard = AppUi.card(this)
        val summaryRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        listOf(
            Triple(
                String.format(
                    Locale.US,
                    "$%.0f/h",
                    avgHourly
                ),
                "Promedio Top ${top.size}",
                AppUi.GREEN
            ),
            Triple(
                String.format(
                    Locale.US,
                    "$%.0f/h",
                    dayStats.bestHourly
                ),
                "Máximo del día",
                AppUi.ORANGE
            ),
            Triple(
                cutoffText(cutoff),
                "Corte para entrar",
                AppUi.scoreColor(
                    cutoff.score
                )
            )
        ).forEach {
            (value, label, color) ->

            summaryRow.addView(
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
                        AppUi.dp(this@TopTenActivity, 3),
                        0,
                        AppUi.dp(this@TopTenActivity, 3),
                        0
                    )
                }
            )
        }

        summaryCard.addView(summaryRow)

        summaryCard.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "Promedio Top: $%.0f/h · $%.2f/km · %.0f/100   ·   Mejor $%.2f/km",
                    avgHourly,
                    avgKm,
                    avgScore,
                    dayStats.bestKm
                ),
                12.5f,
                AppUi.TEXT
            ).apply {
                setPadding(
                    AppUi.dp(this@TopTenActivity, 4),
                    AppUi.dp(this@TopTenActivity, 12),
                    AppUi.dp(this@TopTenActivity, 4),
                    0
                )
            }
        )

        content.addView(summaryCard)

        content.addView(
            AppUi.sectionTitle(
                this,
                "Ranking"
            )
        )

        val formatter =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            )

        top.forEachIndexed {
            index,
            item ->

            content.addView(
                rankingCard(
                    index + 1,
                    item,
                    formatter
                ),
                AppUi.fullWidth(
                    this,
                    0,
                    8
                )
            )
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
                    AppUi.dp(this@TopTenActivity, 10),
                    0,
                    0
                )
            }
        )
    }

    private fun dateSelectorCard(): LinearLayout {
        val card = AppUi.card(this)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            dateArrow("‹") {
                selectedDayStart =
                    previousDayStart(
                        selectedDayStart
                    )
                render()
            }
        )

        row.addView(
            TextView(this).apply {
                text =
                    longDateLabel(
                        selectedDayStart
                    )
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(AppUi.TEXT)
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
                setPadding(
                    AppUi.dp(this@TopTenActivity, 8),
                    AppUi.dp(this@TopTenActivity, 9),
                    AppUi.dp(this@TopTenActivity, 8),
                    AppUi.dp(this@TopTenActivity, 9)
                )
                background = AppUi.rounded(
                    this@TopTenActivity,
                    AppUi.BG,
                    13,
                    AppUi.BORDER
                )
                setOnClickListener {
                    openDatePicker()
                }
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(
                    AppUi.dp(this@TopTenActivity, 7),
                    0,
                    AppUi.dp(this@TopTenActivity, 7),
                    0
                )
            }
        )

        row.addView(
            dateArrow("›") {
                val candidate =
                    nextDayStart(
                        selectedDayStart
                    )

                if (
                    candidate <=
                    startOfToday()
                ) {
                    selectedDayStart =
                        candidate
                    render()
                }
            }
        )

        card.addView(row)

        card.addView(
            AppUi.body(
                this,
                "Toca la fecha para elegir otro día del calendario",
                11f
            ).apply {
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    AppUi.dp(this@TopTenActivity, 7),
                    0,
                    0
                )
            }
        )

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
                this@TopTenActivity,
                AppUi.ORANGE_TINT,
                13,
                AppUi.ORANGE
            )
            setPadding(
                AppUi.dp(this@TopTenActivity, 11),
                AppUi.dp(this@TopTenActivity, 3),
                AppUi.dp(this@TopTenActivity, 11),
                AppUi.dp(this@TopTenActivity, 3)
            )
            setOnClickListener {
                action()
            }
        }

    private fun cutoffText(
        item: HistoryEntry
    ): String =
        when (mode) {
            HistoryTopMode.SCORE ->
                "${item.score}/100"

            HistoryTopMode.HOURLY ->
                String.format(
                    Locale.US,
                    "$%.0f/h",
                    item.pesosPerHour
                )

            HistoryTopMode.KM ->
                String.format(
                    Locale.US,
                    "$%.2f/km",
                    item.pesosPerKm
                )
        }

    private fun rankingCard(
        rank: Int,
        item: HistoryEntry,
        formatter: SimpleDateFormat
    ): LinearLayout {
        val card =
            AppUi.card(
                this,
                Color.WHITE,
                12
            )

        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val rankColor =
            when (rank) {
                1 ->
                    Color.rgb(
                        255,
                        183,
                        0
                    )

                2 ->
                    Color.rgb(
                        145,
                        153,
                        164
                    )

                3 ->
                    Color.rgb(
                        205,
                        126,
                        67
                    )

                else ->
                    AppUi.BORDER
            }

        row.addView(
            TextView(this).apply {
                text =
                    rank.toString()
                gravity =
                    Gravity.CENTER
                textSize =
                    15f
                setTextColor(
                    if (rank <= 3) {
                        Color.WHITE
                    } else {
                        AppUi.TEXT
                    }
                )
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
                background =
                    AppUi.rounded(
                        this@TopTenActivity,
                        rankColor,
                        99
                    )
                setPadding(
                    AppUi.dp(
                        this@TopTenActivity,
                        9
                    ),
                    AppUi.dp(
                        this@TopTenActivity,
                        5
                    ),
                    AppUi.dp(
                        this@TopTenActivity,
                        9
                    ),
                    AppUi.dp(
                        this@TopTenActivity,
                        5
                    )
                )
            }
        )

        val middle =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    AppUi.dp(
                        this@TopTenActivity,
                        11
                    ),
                    0,
                    0,
                    0
                )
            }

        middle.addView(
            AppUi.title(
                this,
                String.format(
                    Locale.US,
                    "$%.2f",
                    item.fare
                ),
                16f
            )
        )

        middle.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "%d min · %.1f km · %s",
                    item.totalMinutes,
                    item.totalKm,
                    formatter.format(
                        Date(
                            item.firstSeen
                        )
                    )
                ),
                11.5f
            )
        )

        row.addView(
            middle,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val values =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                gravity =
                    Gravity.END
            }

        values.addView(
            TextView(this).apply {
                text =
                    String.format(
                        Locale.US,
                        "$%.0f/h",
                        item.pesosPerHour
                    )
                textSize =
                    16f
                setTextColor(
                    AppUi.GREEN
                )
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
                gravity =
                    Gravity.END
            }
        )

        values.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "$%.2f/km",
                    item.pesosPerKm
                ),
                11.5f,
                AppUi.TEXT
            ).apply {
                gravity =
                    Gravity.END
            }
        )

        row.addView(values)

        row.addView(
            TextView(this).apply {
                text =
                    item.score.toString()
                textSize =
                    13f
                gravity =
                    Gravity.CENTER
                setTextColor(
                    AppUi.scoreColor(
                        item.score
                    )
                )
                background =
                    AppUi.rounded(
                        this@TopTenActivity,
                        Color.WHITE,
                        99,
                        AppUi.scoreColor(
                            item.score
                        )
                    )
                setPadding(
                    AppUi.dp(
                        this@TopTenActivity,
                        7
                    ),
                    AppUi.dp(
                        this@TopTenActivity,
                        4
                    ),
                    AppUi.dp(
                        this@TopTenActivity,
                        7
                    ),
                    AppUi.dp(
                        this@TopTenActivity,
                        4
                    )
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    AppUi.dp(
                        this@TopTenActivity,
                        10
                    ),
                    0,
                    0,
                    0
                )
            }
        )

        card.addView(row)

        if (item.seenCount > 1) {
            card.addView(
                AppUi.body(
                    this,
                    "Vista ${item.seenCount} veces",
                    10.5f
                ).apply {
                    gravity =
                        Gravity.END
                    setPadding(
                        0,
                        AppUi.dp(
                            this@TopTenActivity,
                            4
                        ),
                        0,
                        0
                    )
                }
            )
        }

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
            .setTitle(
                "Eliminar propuesta"
            )
            .setMessage(
                String.format(
                    Locale.US,
                    "¿Eliminar la propuesta de $%.2f ($%.0f/h, $%.2f/km)?",
                    item.fare,
                    item.pesosPerHour,
                    item.pesosPerKm
                )
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Eliminar"
            ) {
                    _,
                    _ ->

                repository.deleteById(
                    item.id
                )
                render()
            }
            .show()
    }

    private fun openDatePicker() {
        val c =
            Calendar.getInstance().apply {
                timeInMillis =
                    selectedDayStart
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
            if (
                millis ==
                startOfToday()
            ) {
                "Hoy · "
            } else {
                ""
            }

        val format =
            SimpleDateFormat(
                "EEE d 'de' MMM yyyy",
                Locale(
                    "es",
                    "MX"
                )
            )

        return prefix +
            format.format(
                Date(millis)
            ).replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale(
                            "es",
                            "MX"
                        )
                    )
                } else {
                    it.toString()
                }
            }
    }

    private fun startOfToday(): Long {
        val c =
            Calendar.getInstance()

        return startOfDay(
            c.get(
                Calendar.YEAR
            ),
            c.get(
                Calendar.MONTH
            ),
            c.get(
                Calendar.DAY_OF_MONTH
            )
        )
    }

    private fun startOfDay(
        year: Int,
        month: Int,
        day: Int
    ): Long =
        Calendar.getInstance().apply {
            set(
                Calendar.YEAR,
                year
            )
            set(
                Calendar.MONTH,
                month
            )
            set(
                Calendar.DAY_OF_MONTH,
                day
            )
            set(
                Calendar.HOUR_OF_DAY,
                0
            )
            set(
                Calendar.MINUTE,
                0
            )
            set(
                Calendar.SECOND,
                0
            )
            set(
                Calendar.MILLISECOND,
                0
            )
        }.timeInMillis

    private fun previousDayStart(
        millis: Long
    ): Long =
        Calendar.getInstance().apply {
            timeInMillis =
                millis
            add(
                Calendar.DAY_OF_MONTH,
                -1
            )
        }.timeInMillis

    private fun nextDayStart(
        millis: Long
    ): Long =
        Calendar.getInstance().apply {
            timeInMillis =
                millis
            add(
                Calendar.DAY_OF_MONTH,
                1
            )
        }.timeInMillis

    companion object {
        const val EXTRA_DAY_START =
            "top_day_start"
    }
}
