package com.jonathan.didiprofit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var preferences: ProfitPreferences
    private lateinit var history: HistoryRepository

    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var resumeButton: Button

    private lateinit var todayHourly: TextView
    private lateinit var todayKm: TextView
    private lateinit var todayScore: TextView
    private lateinit var todayCount: TextView

    private var handledResumeIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ProfitPreferences(this)
        history = HistoryRepository(this)

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContentView(buildUi())
        requestNotificationPermissionIfNeeded()
        handleResumeIntentIfNeeded(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handledResumeIntent = false
        handleResumeIntentIfNeeded(intent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateDashboard()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == REQUEST_CAPTURE ||
            requestCode == REQUEST_CAPTURE_TIMER_RESUME
        ) {
            val wasTimerResume =
                requestCode == REQUEST_CAPTURE_TIMER_RESUME

            if (
                resultCode == RESULT_OK &&
                data != null
            ) {
                /*
                 * During timer resume, the existing overlay must stay attached:
                 * it owns the running countdown until CaptureService confirms
                 * that a new MediaProjection session is ready.
                 */
                if (!wasTimerResume) {
                    OverlayController.removeAnyOverlay(this)
                }

                AnalysisResumeState.clear(this)

                val service = Intent(
                    this,
                    CaptureService::class.java
                ).apply {
                    putExtra(
                        CaptureService.EXTRA_RESULT_CODE,
                        resultCode
                    )
                    putExtra(
                        CaptureService.EXTRA_RESULT_DATA,
                        data
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service)
                } else {
                    startService(service)
                }

                Toast.makeText(
                    this,
                    if (wasTimerResume) {
                        "Análisis reanudado"
                    } else {
                        "Análisis iniciado. Abre DiDi Conductor."
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }

            /*
             * This Activity was opened from the overlay only to request a new
             * capture grant. Returning immediately puts DiDi back in front.
             * If permission was cancelled, the timer continues unchanged.
             */
            if (wasTimerResume) {
                finish()
                return
            }
        }

        updateStatus()
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
                AppUi.dp(this@MainActivity, 18),
                AppUi.dp(this@MainActivity, 18),
                AppUi.dp(this@MainActivity, 18),
                AppUi.dp(this@MainActivity, 28)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        headerText.addView(AppUi.title(this, "DiDi Rentabilidad", 27f))
        headerText.addView(
            AppUi.body(
                this,
                "V3.7.0 · Análisis, historial y Pon Tu Precio",
                12.5f
            ).apply {
                setPadding(0, AppUi.dp(this@MainActivity, 3), 0, 0)
            }
        )
        header.addView(
            headerText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val settingsShortcut = TextView(this).apply {
            text = "⚙"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(AppUi.TEXT)
            background = AppUi.rounded(
                this@MainActivity,
                Color.WHITE,
                16,
                AppUi.BORDER
            )
            setPadding(
                AppUi.dp(this@MainActivity, 11),
                AppUi.dp(this@MainActivity, 8),
                AppUi.dp(this@MainActivity, 11),
                AppUi.dp(this@MainActivity, 8)
            )
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        header.addView(settingsShortcut)
        content.addView(header)

        statusCard = AppUi.card(this, AppUi.GREEN_TINT, 14).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusIcon = TextView(this).apply {
            text = "✓"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = AppUi.rounded(
                this@MainActivity,
                AppUi.GREEN,
                99
            )
            setPadding(
                AppUi.dp(this@MainActivity, 7),
                AppUi.dp(this@MainActivity, 3),
                AppUi.dp(this@MainActivity, 7),
                AppUi.dp(this@MainActivity, 3)
            )
        }
        statusCard.addView(statusIcon)

        val statusTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(this@MainActivity, 12), 0, 0, 0)
        }
        statusTitle = AppUi.title(this, "Superposición permitida", 16f)
        statusBody = AppUi.body(this, "Todo listo para analizar", 12.5f)
        statusTexts.addView(statusTitle)
        statusTexts.addView(statusBody)
        statusCard.addView(
            statusTexts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        content.addView(
            statusCard,
            AppUi.fullWidth(this, 16, 0)
        )

        content.addView(AppUi.sectionTitle(this, "Resumen de hoy"))

        val summaryCard = AppUi.card(this)
        val summaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun liveMetric(): Pair<LinearLayout, TextView> {
            val valueView = TextView(this).apply {
                text = "—"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(AppUi.TEXT)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    AppUi.dp(this@MainActivity, 5),
                    AppUi.dp(this@MainActivity, 11),
                    AppUi.dp(this@MainActivity, 5),
                    AppUi.dp(this@MainActivity, 11)
                )
                background = AppUi.rounded(
                    this@MainActivity,
                    Color.rgb(250, 250, 251),
                    14,
                    AppUi.BORDER
                )
                addView(valueView)
            }
            return tile to valueView
        }

        val (hourTile, hourView) = liveMetric()
        todayHourly = hourView
        hourTile.addView(AppUi.body(this, "Promedio $/h", 11f).apply {
            gravity = Gravity.CENTER
        })

        val (kmTile, kmView) = liveMetric()
        todayKm = kmView
        kmTile.addView(AppUi.body(this, "Promedio $/km", 11f).apply {
            gravity = Gravity.CENTER
        })

        val (scoreTile, scoreView) = liveMetric()
        todayScore = scoreView
        scoreTile.addView(AppUi.body(this, "Puntaje", 11f).apply {
            gravity = Gravity.CENTER
        })

        listOf(hourTile, kmTile, scoreTile).forEach { tile ->
            summaryRow.addView(
                tile,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        AppUi.dp(this@MainActivity, 3),
                        0,
                        AppUi.dp(this@MainActivity, 3),
                        0
                    )
                }
            )
        }
        summaryCard.addView(summaryRow)

        todayCount = AppUi.body(this, "Sin propuestas hoy", 12.5f).apply {
            setPadding(
                AppUi.dp(this@MainActivity, 4),
                AppUi.dp(this@MainActivity, 11),
                AppUi.dp(this@MainActivity, 4),
                0
            )
        }
        summaryCard.addView(todayCount)
        content.addView(summaryCard)

        content.addView(AppUi.sectionTitle(this, "Análisis"))

        content.addView(
            AppUi.primaryButton(this, "▶  Iniciar análisis") {
                startAnalysisFlow()
            },
            AppUi.fullWidth(this, 0, 9)
        )

        resumeButton = AppUi.secondaryButton(this, "↻  Reanudar análisis") {
            startAnalysisFlow()
        }.apply {
            visibility = View.GONE
        }
        content.addView(
            resumeButton,
            AppUi.fullWidth(this, 0, 9)
        )

        content.addView(
            AppUi.secondaryButton(this, "▣  Permitir superposición") {
                openOverlayPermission()
            },
            AppUi.fullWidth(this, 0, 9)
        )

        content.addView(
            AppUi.secondaryButton(this, "■  Detener análisis") {
                stopAnalysis()
            },
            AppUi.fullWidth(this, 0, 9)
        )

        content.addView(
            AppUi.secondaryButton(this, "▥  Historial y estadísticas") {
                startActivity(Intent(this, HistoryActivity::class.java))
            },
            AppUi.fullWidth(this, 0, 0)
        )

        val t = preferences.load()
        content.addView(AppUi.sectionTitle(this, "Tu estrategia"))

        val strategyCard = AppUi.card(this)
        strategyCard.addView(
            AppUi.title(this, "Semáforo de rentabilidad", 16f)
        )
        strategyCard.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "Excelente: $%.0f/h y $%.2f/km   ·   Bueno: $%.0f/h y $%.2f/km",
                    t.hourlyExcellent,
                    t.kmExcellent,
                    t.hourlyGood,
                    t.kmGood
                ),
                13f
            ).apply {
                setPadding(0, AppUi.dp(this@MainActivity, 5), 0, AppUi.dp(this@MainActivity, 12))
            }
        )

        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(
            "Excelente" to AppUi.GREEN,
            "Bueno" to AppUi.AMBER,
            "Baja" to AppUi.RED
        ).forEach { (label, color) ->
            dots.addView(
                TextView(this).apply {
                    text = "● $label"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(color)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        strategyCard.addView(dots)

        strategyCard.addView(
            AppUi.body(
                this,
                String.format(
                    Locale.US,
                    "Pon Tu Precio: %.0f / %.0f / %.0f $/hora",
                    t.targetHourly1,
                    t.targetHourly2,
                    t.targetHourly3
                ),
                13f,
                AppUi.TEXT
            ).apply {
                setPadding(0, AppUi.dp(this@MainActivity, 14), 0, 0)
            }
        )

        strategyCard.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        content.addView(strategyCard)

        val note = AppUi.card(this, AppUi.ORANGE_TINT, 14)
        note.addView(AppUi.title(this, "Consejo", 15f))
        note.addView(
            AppUi.body(
                this,
                "Usa Ajustes para cambiar los umbrales y objetivos. El motor de análisis de V3.3.2 se conserva sin cambios.",
                12.5f
            ).apply {
                setPadding(0, AppUi.dp(this@MainActivity, 4), 0, 0)
            }
        )
        content.addView(note, AppUi.fullWidth(this, 14, 0))

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(AppUi.bottomNav(this, AppSection.HOME))
        return root
    }

    private fun updateDashboard() {
        val stats = history.statsSince(startOfToday())

        if (stats.count <= 0) {
            todayHourly.text = "—"
            todayKm.text = "—"
            todayScore.text = "—"
            todayScore.setTextColor(AppUi.TEXT)
            todayCount.text = "Sin propuestas hoy"
            return
        }

        todayHourly.text = String.format(Locale.US, "$%.0f", stats.avgHourly)
        todayKm.text = String.format(Locale.US, "$%.2f", stats.avgKm)
        todayScore.text = String.format(Locale.US, "%.0f/100", stats.avgScore)
        todayScore.setTextColor(
            AppUi.scoreColor(stats.avgScore.toInt())
        )
        todayCount.text = String.format(
            Locale.US,
            "%d propuestas únicas · Mejor $%.0f/h · $%.2f/km",
            stats.count,
            stats.bestHourly,
            stats.bestKm
        )
    }

    private fun updateStatus() {
        val pending = AnalysisResumeState.isPending(this)
        resumeButton.visibility = if (pending) View.VISIBLE else View.GONE

        when {
            pending -> {
                statusCard.background = AppUi.rounded(
                    this,
                    AppUi.ORANGE_TINT,
                    18,
                    AppUi.ORANGE
                )
                statusTitle.text = "Análisis pausado"
                statusTitle.setTextColor(AppUi.ORANGE_DARK)
                statusBody.text =
                    if (AnalysisResumeState.reason(this) ==
                        AnalysisResumeState.REASON_SCREEN_LOCK
                    ) {
                        "Android pausó la captura al bloquear la pantalla. Toca Reanudar análisis."
                    } else {
                        "La captura se detuvo. Toca Reanudar análisis."
                    }
            }

            Settings.canDrawOverlays(this) -> {
                statusCard.background = AppUi.rounded(
                    this,
                    AppUi.GREEN_TINT,
                    18,
                    AppUi.GREEN
                )
                statusTitle.text = "Superposición permitida"
                statusTitle.setTextColor(AppUi.GREEN)
                statusBody.text = "Todo listo para analizar propuestas"
            }

            else -> {
                statusCard.background = AppUi.rounded(
                    this,
                    Color.WHITE,
                    18,
                    AppUi.BORDER
                )
                statusTitle.text = "Falta permiso de superposición"
                statusTitle.setTextColor(AppUi.TEXT)
                statusBody.text = "Autorízalo una sola vez antes de iniciar el análisis"
            }
        }
    }

    private fun stopAnalysis() {
        AnalysisResumeState.clear(this)
        OverlayController.removeAnyOverlay(this)
        stopService(Intent(this, CaptureService::class.java))
        Toast.makeText(this, "Análisis detenido", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun handleResumeIntentIfNeeded(intent: Intent?) {
        if (handledResumeIntent) return

        when (intent?.action) {
            CaptureService.ACTION_RESUME_FROM_TIMER -> {
                handledResumeIntent = true
                intent.action = null

                /*
                 * Keep the timer overlay visible while Android asks for a new
                 * screen-capture grant.
                 */
                startAnalysisFlow(
                    removeExistingOverlay = false,
                    requestCode = REQUEST_CAPTURE_TIMER_RESUME
                )
            }

            AnalysisResumeState.ACTION_RESUME_ANALYSIS -> {
                handledResumeIntent = true
                intent.action = null

                if (AnalysisResumeState.isPending(this)) {
                    startAnalysisFlow()
                }
            }
        }
    }

    private fun openOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "El permiso ya está concedido",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun startAnalysisFlow(
        removeExistingOverlay: Boolean = true,
        requestCode: Int = REQUEST_CAPTURE
    ) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Primero permite la superposición",
                Toast.LENGTH_LONG
            ).show()
            openOverlayPermission()
            return
        }

        if (removeExistingOverlay) {
            OverlayController.removeAnyOverlay(this)
        }

        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            requestCode
        )
    }

    private fun startOfToday(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                200
            )
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 1001
        private const val REQUEST_CAPTURE_TIMER_RESUME = 1002
    }
}
