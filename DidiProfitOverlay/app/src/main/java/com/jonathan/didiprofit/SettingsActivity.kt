package com.jonathan.didiprofit

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var preferences: ProfitPreferences

    private lateinit var hourlyExcellent: EditText
    private lateinit var hourlyGood: EditText
    private lateinit var kmExcellent: EditText
    private lateinit var kmGood: EditText

    private lateinit var target1: EditText
    private lateinit var target2: EditText
    private lateinit var target3: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ProfitPreferences(this)

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.BG)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AppUi.dp(this@SettingsActivity, 18),
                AppUi.dp(this@SettingsActivity, 18),
                AppUi.dp(this@SettingsActivity, 18),
                AppUi.dp(this@SettingsActivity, 28)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(AppUi.TEXT)
            setPadding(0, 0, AppUi.dp(this@SettingsActivity, 12), 0)
            setOnClickListener { finish() }
        })
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titles.addView(AppUi.title(this, "Ajustes", 25f))
        titles.addView(AppUi.body(this, "Personaliza tu estrategia", 12f))
        header.addView(
            titles,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        content.addView(header)

        val t = preferences.load()

        content.addView(AppUi.sectionTitle(this, "Semáforo"))

        val semaphore = AppUi.card(this)
        semaphore.addView(
            AppUi.body(
                this,
                "Define cuándo una propuesta se considera excelente, buena o baja. Los colores del overlay y el puntaje utilizan estos valores.",
                13f
            )
        )

        hourlyExcellent = settingField(
            semaphore,
            "●  $/hora excelente",
            AppUi.GREEN,
            t.hourlyExcellent,
            "Ej. 200"
        )
        hourlyGood = settingField(
            semaphore,
            "●  $/hora bueno",
            AppUi.AMBER,
            t.hourlyGood,
            "Ej. 180"
        )
        kmExcellent = settingField(
            semaphore,
            "●  $/km excelente",
            AppUi.GREEN,
            t.kmExcellent,
            "Ej. 8"
        )
        kmGood = settingField(
            semaphore,
            "●  $/km bueno",
            AppUi.AMBER,
            t.kmGood,
            "Ej. 6"
        )
        content.addView(semaphore)

        content.addView(AppUi.sectionTitle(this, "Sugerencias Pon Tu Precio"))

        val targets = AppUi.card(this)
        targets.addView(
            AppUi.body(
                this,
                "Elige tres objetivos de ingreso por hora. El overlay calculará la tarifa mínima necesaria considerando recogida + viaje.",
                13f
            )
        )

        val targetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, AppUi.dp(this@SettingsActivity, 13), 0, 0)
        }

        fun targetColumn(
            label: String,
            value: Double
        ): Pair<LinearLayout, EditText> {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            column.addView(
                AppUi.body(
                    this,
                    label,
                    12f,
                    AppUi.TEXT
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, AppUi.dp(this@SettingsActivity, 5))
                }
            )
            val field = AppUi.numberField(this, value, "$/hora").apply {
                gravity = Gravity.CENTER
            }
            column.addView(field)
            return column to field
        }

        val (c1, f1) = targetColumn("Objetivo 1", t.targetHourly1)
        val (c2, f2) = targetColumn("Objetivo 2", t.targetHourly2)
        val (c3, f3) = targetColumn("Objetivo 3", t.targetHourly3)
        target1 = f1
        target2 = f2
        target3 = f3

        listOf(c1, c2, c3).forEach { col ->
            targetRow.addView(
                col,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        AppUi.dp(this@SettingsActivity, 3),
                        0,
                        AppUi.dp(this@SettingsActivity, 3),
                        0
                    )
                }
            )
        }
        targets.addView(targetRow)
        content.addView(targets)

        content.addView(AppUi.sectionTitle(this, "Overlay"))

        val overlayNote = AppUi.card(this, AppUi.GREEN_TINT)
        overlayNote.addView(AppUi.title(this, "Panel flotante estable", 16f))
        overlayNote.addView(
            AppUi.body(
                this,
                "En V3.4 se conserva el comportamiento del overlay de V3.3.2. El rediseño no modifica el OCR, el parser ni la captura de pantalla.",
                13f
            ).apply {
                setPadding(0, AppUi.dp(this@SettingsActivity, 5), 0, 0)
            }
        )
        content.addView(overlayNote)

        content.addView(
            AppUi.primaryButton(this, "Guardar configuración") {
                saveSettings(showToast = true)
            },
            AppUi.fullWidth(this, 20, 0)
        )

        val privacy = AppUi.card(this, AppUi.ORANGE_TINT, 14)
        privacy.addView(AppUi.title(this, "Privacidad", 15f))
        privacy.addView(
            AppUi.body(
                this,
                "DiDi Rentabilidad guarda únicamente los datos numéricos de las propuestas. No guarda capturas de pantalla.",
                12.5f
            ).apply {
                setPadding(0, AppUi.dp(this@SettingsActivity, 4), 0, 0)
            }
        )
        content.addView(privacy, AppUi.fullWidth(this, 14, 0))

        content.addView(
            AppUi.body(
                this,
                "Versión 3.5",
                11.5f
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, AppUi.dp(this@SettingsActivity, 14), 0, 0)
            }
        )

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(AppUi.bottomNav(this, AppSection.SETTINGS))

        return root
    }

    private fun settingField(
        parent: LinearLayout,
        label: String,
        labelColor: Int,
        value: Double,
        hint: String
    ): EditText {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, AppUi.dp(this@SettingsActivity, 13), 0, 0)
        }

        row.addView(
            TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(labelColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val field = AppUi.numberField(this, value, hint).apply {
            gravity = Gravity.CENTER
        }
        row.addView(
            field,
            LinearLayout.LayoutParams(
                AppUi.dp(this@SettingsActivity, 105),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        parent.addView(row)
        return field
    }

    private fun saveSettings(showToast: Boolean) {
        val old = preferences.load()

        fun positive(field: EditText, fallback: Double): Double {
            val parsed = field.text.toString().trim().toDoubleOrNull()
            return if (parsed != null && parsed > 0.0) parsed else fallback
        }

        val updated = Thresholds(
            hourlyExcellent = positive(hourlyExcellent, old.hourlyExcellent),
            hourlyGood = positive(hourlyGood, old.hourlyGood),
            kmExcellent = positive(kmExcellent, old.kmExcellent),
            kmGood = positive(kmGood, old.kmGood),
            targetHourly1 = positive(target1, old.targetHourly1),
            targetHourly2 = positive(target2, old.targetHourly2),
            targetHourly3 = positive(target3, old.targetHourly3)
        )

        preferences.save(updated)

        if (showToast) {
            Toast.makeText(
                this,
                String.format(
                    Locale.US,
                    "Guardado: %.0f / %.0f $/h",
                    updated.hourlyExcellent,
                    updated.hourlyGood
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
