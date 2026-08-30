package com.jonathan.didiprofit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

class OverlayController(
    private val context: Context,
    private val onAnalysisPausedChanged: (Boolean) -> Unit = {},
    private val onTimerStarted: () -> Unit = {},
    private val onResumeAnalysisRequested: () -> Unit = {},
    private val onStopRequested: () -> Unit = {}
) {
    private enum class Mode {
        ANALYZING,
        TIMER_SETUP,
        TIMER_RUNNING,
        TIMER_FINISHED
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var hourlyView: TextView? = null
    private var kmView: TextView? = null
    private var detailsView: TextView? = null
    private var scoreView: TextView? = null
    private var finishEstimateView: TextView? = null
    private var suggestionsTitleView: TextView? = null
    private var suggestionsView: TextView? = null

    private var timerControls: LinearLayout? = null
    private var timerMinusView: TextView? = null
    private var timerMinutesView: TextView? = null
    private var timerPlusView: TextView? = null
    private var timerStartView: TextView? = null
    private var timerFinishView: TextView? = null
    private var timerActionView: TextView? = null
    private var timerCompactView: CircularTimerView? = null

    private var stopTarget: TextView? = null
    private var insideStopZone = false

    private var mode = Mode.ANALYZING
    private var lastOfferMinutes: Int? = null
    private var selectedTimerMinutes = DEFAULT_TIMER_MINUTES
    private var timerStartedElapsed = 0L
    private var timerEndElapsed = 0L
    private var timerFinishWallMillis = 0L
    private var timerDurationMillis = 0L
    private var timerExpanded = false

    private val timerTick = object : Runnable {
        override fun run() {
            if (mode != Mode.TIMER_RUNNING || root == null) return
            updateTimerDisplay()
            mainHandler.postDelayed(this, TIMER_TICK_MS)
        }
    }

    private val density get() = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    fun showWaiting() {
        if (mode != Mode.ANALYZING) return
        ensureCreated()
        prepareStandardPanel()
        hourlyView?.text = "Analizando DiDi…"
        hourlyView?.setTextColor(Color.WHITE)
        kmView?.text = "Esperando propuesta"
        kmView?.setTextColor(Color.LTGRAY)
        detailsView?.text = "Incluye recogida + viaje"
        scoreView?.visibility = View.GONE
        finishEstimateView?.visibility = View.GONE
        finishEstimateView?.text = ""
        suggestionsTitleView?.visibility = View.GONE
        suggestionsView?.visibility = View.GONE
        suggestionsView?.text = ""
        hideTimerControls()
    }

    fun showOffer(offer: RideOffer, thresholds: Thresholds) {
        if (mode != Mode.ANALYZING) return
        ensureCreated()
        prepareStandardPanel()
        lastOfferMinutes = offer.totalMinutes.coerceIn(
            MIN_TIMER_MINUTES,
            MAX_TIMER_MINUTES
        )
        hourlyView?.text = String.format(Locale.US, "$%.0f/h", offer.pesosPerHour)
        hourlyView?.setTextColor(
            scoreColor(
                offer.pesosPerHour,
                thresholds.hourlyExcellent,
                thresholds.hourlyGood
            )
        )
        kmView?.text = String.format(Locale.US, "$%.2f/km", offer.pesosPerKm)
        kmView?.setTextColor(
            scoreColor(
                offer.pesosPerKm,
                thresholds.kmExcellent,
                thresholds.kmGood
            )
        )
        detailsView?.text = String.format(
            Locale.US,
            "%d min · %.1f km · tarifa %.2f",
            offer.totalMinutes,
            offer.totalKilometers,
            offer.fare
        )

        val score = offer.profitabilityScore(thresholds)
        scoreView?.visibility = View.VISIBLE
        scoreView?.text = "Rentabilidad $score/100 · ${offer.profitabilityLabel(thresholds)}"
        scoreView?.setTextColor(score100Color(score))

        val estimatedFinish = TripTimeEstimator.estimatedFinishMillis(offer)
        if (estimatedFinish != null) {
            val timeText = DateFormat.getTimeFormat(context).format(Date(estimatedFinish))
            finishEstimateView?.visibility = View.VISIBLE
            finishEstimateView?.text = "Fin aprox. si aceptas: $timeText"
        } else {
            finishEstimateView?.visibility = View.GONE
            finishEstimateView?.text = ""
        }

        suggestionsTitleView?.visibility = View.VISIBLE
        suggestionsView?.visibility = View.VISIBLE
        suggestionsView?.text = listOf(
            suggestionLine(offer, thresholds.targetHourly1),
            suggestionLine(offer, thresholds.targetHourly2),
            suggestionLine(offer, thresholds.targetHourly3)
        ).joinToString("\n")
        hideTimerControls()
    }

    fun remove() {
        mainHandler.removeCallbacks(timerTick)
        hideStopTarget()
        val view = root
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
            clearRegisteredOverlay(view)
        }
        root = null
        hourlyView = null
        kmView = null
        detailsView = null
        scoreView = null
        finishEstimateView = null
        suggestionsTitleView = null
        suggestionsView = null
        timerControls = null
        timerMinusView = null
        timerMinutesView = null
        timerPlusView = null
        timerStartView = null
        timerFinishView = null
        timerActionView = null
        timerCompactView = null
    }

    private fun ensureCreated() {
        if (root != null) return
        removeAnyOverlay(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 24, 24, 24))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.argb(100, 255, 255, 255))
            }
        }

        hourlyView = TextView(context).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        kmView = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        detailsView = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        scoreView = TextView(context).apply {
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
            visibility = View.GONE
        }
        finishEstimateView = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.rgb(125, 211, 252))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
            visibility = View.GONE
        }
        suggestionsTitleView = TextView(context).apply {
            text = "Pon Tu Precio · mínimo"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
            visibility = View.GONE
        }
        suggestionsView = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = View.GONE
        }

        timerMinusView = timerAdjustButton("−") {
            adjustTimerMinutes(-1)
        }
        timerMinutesView = TextView(context).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            minWidth = dp(92)
            minHeight = dp(44)
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        timerPlusView = timerAdjustButton("+") {
            adjustTimerMinutes(1)
        }

        timerControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
            addView(timerMinusView)
            addView(timerMinutesView)
            addView(timerPlusView)
        }

        timerStartView = actionButton(
            "▶ COMENZAR",
            primary = true
        ) {
            startTimer()
        }.apply {
            visibility = View.GONE
        }

        timerFinishView = actionButton(
            "■ FINALIZAR VIAJE",
            primary = true
        ) {
            // A normal tap intentionally does nothing.
        }.apply {
            visibility = View.GONE

            var holdRunnable: Runnable? = null

            setOnTouchListener {
                    button,
                    event ->

                fun cancelHold() {
                    holdRunnable?.let {
                        mainHandler.removeCallbacks(it)
                    }
                    holdRunnable = null
                    button.isPressed = false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        button.isPressed = true

                        val action =
                            Runnable {
                                holdRunnable = null

                                if (
                                    mode == Mode.TIMER_RUNNING &&
                                    timerExpanded &&
                                    button.isPressed
                                ) {
                                    button.performHapticFeedback(
                                        HapticFeedbackConstants.LONG_PRESS
                                    )
                                    button.isPressed = false
                                    finishTrip()
                                }
                            }

                        holdRunnable = action
                        mainHandler.postDelayed(
                            action,
                            FINISH_HOLD_MS
                        )
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (
                            event.x < 0f ||
                            event.y < 0f ||
                            event.x > button.width ||
                            event.y > button.height
                        ) {
                            cancelHold()
                        }
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        cancelHold()
                        true
                    }

                    else -> true
                }
            }
        }

        timerActionView = actionButton(
            "Cancelar",
            primary = false
        ) {
            resumeAnalysis()
        }.apply {
            visibility = View.GONE
        }

        timerCompactView =
            CircularTimerView(context).apply {
                visibility = View.GONE
                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(COMPACT_TIMER_SIZE_DP),
                        dp(COMPACT_TIMER_SIZE_DP)
                    ).apply {
                        gravity = Gravity.CENTER
                    }
            }

        container.addView(hourlyView)
        container.addView(kmView)
        container.addView(detailsView)
        container.addView(scoreView)
        container.addView(finishEstimateView)
        container.addView(suggestionsTitleView)
        container.addView(suggestionsView)
        container.addView(timerCompactView)
        container.addView(timerControls)
        container.addView(timerStartView)
        container.addView(timerFinishView)
        container.addView(timerActionView)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(150)
        }

        makeDraggable(container, lp)
        windowManager.addView(container, lp)
        root = container
        registerOverlay(windowManager, container)
    }

    private fun enterTimerSetup() {
        if (mode != Mode.ANALYZING) return

        ensureCreated()
        prepareStandardPanel()
        mainHandler.removeCallbacks(timerTick)

        mode = Mode.TIMER_SETUP
        onAnalysisPausedChanged(true)

        selectedTimerMinutes = (
            lastOfferMinutes ?: DEFAULT_TIMER_MINUTES
        ).coerceIn(
            MIN_TIMER_MINUTES,
            MAX_TIMER_MINUTES
        )

        hourlyView?.text = "⏱ Temporizador de viaje"
        hourlyView?.setTextColor(Color.WHITE)

        kmView?.text = "Ajusta el tiempo total"
        kmView?.setTextColor(Color.LTGRAY)

        detailsView?.setTextColor(Color.LTGRAY)
        detailsView?.text =
            lastOfferMinutes?.let {
                "Precargado de la última propuesta: $it min"
            } ?: "Tiempo inicial: $DEFAULT_TIMER_MINUTES min"

        hideAnalysisDetails()

        timerControls?.visibility = View.VISIBLE
        timerStartView?.visibility = View.VISIBLE
        timerFinishView?.visibility = View.GONE
        timerActionView?.apply {
            text = "Cancelar"
            visibility = View.VISIBLE
        }

        updateTimerMinutesText()
        root?.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK
        )
    }

    private fun adjustTimerMinutes(delta: Int) {
        if (mode != Mode.TIMER_SETUP) return

        selectedTimerMinutes =
            (selectedTimerMinutes + delta).coerceIn(
                MIN_TIMER_MINUTES,
                MAX_TIMER_MINUTES
            )

        updateTimerMinutesText()
    }

    private fun updateTimerMinutesText() {
        timerMinutesView?.text =
            "$selectedTimerMinutes min"
    }

    private fun startTimer() {
        if (mode != Mode.TIMER_SETUP) return

        val nowElapsed =
            SystemClock.elapsedRealtime()

        timerDurationMillis =
            selectedTimerMinutes * 60_000L

        timerStartedElapsed =
            nowElapsed

        timerEndElapsed =
            nowElapsed + timerDurationMillis

        timerFinishWallMillis =
            System.currentTimeMillis() +
                timerDurationMillis

        mode = Mode.TIMER_RUNNING
        timerExpanded = false

        /*
         * MediaProjection is fully released here, as in V3.6.2.
         * V3.7 only changes the timer UI while the ride is running.
         */
        onTimerStarted()

        enterCompactTimerUi()
        updateTimerDisplay()

        mainHandler.removeCallbacks(timerTick)
        mainHandler.postDelayed(
            timerTick,
            TIMER_TICK_MS
        )
    }

    private fun updateTimerDisplay() {
        if (mode != Mode.TIMER_RUNNING) return

        val nowElapsed =
            SystemClock.elapsedRealtime()

        val remaining =
            timerEndElapsed - nowElapsed

        val elapsed =
            (nowElapsed - timerStartedElapsed)
                .coerceAtLeast(0L)

        val progress =
            if (timerDurationMillis > 0L) {
                (
                    elapsed.toDouble() /
                        timerDurationMillis.toDouble()
                    )
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            } else {
                1f
            }

        if (remaining > 0L) {
            timerCompactView?.setTimerState(
                text =
                    formatDuration(
                        remaining,
                        roundUp = true
                    ),
                progressFraction = progress,
                isOvertime = false
            )
        } else {
            val extra =
                (nowElapsed - timerEndElapsed)
                    .coerceAtLeast(0L)

            timerCompactView?.setTimerState(
                text =
                    "+${
                        formatDuration(
                            extra,
                            roundUp = false
                        )
                    }",
                progressFraction = 1f,
                isOvertime = true
            )
        }

        if (!timerExpanded) return

        updateExpandedTimerDisplay(
            nowElapsed = nowElapsed,
            remaining = remaining
        )
    }

    private fun updateExpandedTimerDisplay(
        nowElapsed: Long,
        remaining: Long
    ) {
        val finishText =
            DateFormat.getTimeFormat(context)
                .format(
                    Date(timerFinishWallMillis)
                )

        hideAnalysisDetails()

        hourlyView?.visibility = View.VISIBLE
        kmView?.visibility = View.VISIBLE
        detailsView?.visibility = View.VISIBLE

        timerCompactView?.visibility = View.GONE
        timerControls?.visibility = View.GONE
        timerStartView?.visibility = View.GONE
        timerFinishView?.visibility = View.VISIBLE
        timerActionView?.visibility = View.GONE

        if (remaining > 0L) {
            hourlyView?.text =
                "⏱ ${
                    formatDuration(
                        remaining,
                        roundUp = true
                    )
                } restantes"
            hourlyView?.setTextColor(Color.WHITE)

            kmView?.text =
                "Fin aprox. ${
                    DateFormat.getTimeFormat(context)
                        .format(
                            Date(timerFinishWallMillis)
                        )
                }"
            kmView?.setTextColor(
                Color.rgb(125, 211, 252)
            )

            detailsView?.setTextColor(
                Color.LTGRAY
            )
            detailsView?.text =
                "Tiempo estimado: $selectedTimerMinutes min"
        } else {
            val extra =
                (nowElapsed - timerEndElapsed)
                    .coerceAtLeast(0L)

            val total =
                timerDurationMillis + extra

            hourlyView?.text =
                "⏱ +${
                    formatDuration(
                        extra,
                        roundUp = false
                    )
                }"
            hourlyView?.setTextColor(
                Color.rgb(255, 204, 0)
            )

            kmView?.text =
                "Tiempo adicional"
            kmView?.setTextColor(
                Color.rgb(255, 204, 0)
            )

            detailsView?.setTextColor(
                Color.LTGRAY
            )
            detailsView?.text =
                "Fin estimado: $finishText · Total ${
                    formatDuration(
                        total,
                        roundUp = false
                    )
                }"
        }
    }

    private fun enterCompactTimerUi() {
        if (mode != Mode.TIMER_RUNNING) return

        timerExpanded = false

        root?.apply {
            setPadding(0, 0, 0, 0)
            background = null
        }

        hourlyView?.visibility = View.GONE
        kmView?.visibility = View.GONE
        detailsView?.visibility = View.GONE
        scoreView?.visibility = View.GONE
        finishEstimateView?.visibility = View.GONE
        suggestionsTitleView?.visibility = View.GONE
        suggestionsView?.visibility = View.GONE

        timerControls?.visibility = View.GONE
        timerStartView?.visibility = View.GONE
        timerFinishView?.visibility = View.GONE
        timerActionView?.visibility = View.GONE

        timerCompactView?.visibility = View.VISIBLE
    }

    private fun expandTimerPanel() {
        if (
            mode != Mode.TIMER_RUNNING ||
            timerExpanded
        ) return

        timerExpanded = true
        restorePanelChrome()
        timerCompactView?.visibility = View.GONE
        updateTimerDisplay()

        root?.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK
        )
    }

    private fun collapseTimerPanel() {
        if (
            mode != Mode.TIMER_RUNNING ||
            !timerExpanded
        ) return

        enterCompactTimerUi()
        updateTimerDisplay()

        root?.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK
        )
    }

    private fun finishTrip() {
        if (mode != Mode.TIMER_RUNNING) return

        val nowElapsed =
            SystemClock.elapsedRealtime()

        val actualMillis =
            (nowElapsed - timerStartedElapsed)
                .coerceAtLeast(0L)

        val differenceMillis =
            actualMillis - timerDurationMillis

        mainHandler.removeCallbacks(timerTick)
        mode = Mode.TIMER_FINISHED
        timerExpanded = true

        restorePanelChrome()
        timerCompactView?.visibility = View.GONE
        hourlyView?.visibility = View.VISIBLE
        kmView?.visibility = View.VISIBLE
        detailsView?.visibility = View.VISIBLE

        hideAnalysisDetails()

        timerControls?.visibility = View.GONE
        timerStartView?.visibility = View.GONE
        timerFinishView?.visibility = View.GONE
        timerActionView?.apply {
            text = "▶ REANUDAR ANÁLISIS"
            visibility = View.VISIBLE
        }

        hourlyView?.text =
            "✓ Viaje finalizado"
        hourlyView?.setTextColor(
            Color.rgb(80, 220, 120)
        )

        val toleranceMillis = 1_000L

        when {
            differenceMillis < -toleranceMillis -> {
                kmView?.text =
                    "✓ ${
                        formatDuration(
                            -differenceMillis,
                            roundUp = false
                        )
                    } antes de lo estimado"
                kmView?.setTextColor(
                    Color.rgb(80, 220, 120)
                )
            }

            differenceMillis > toleranceMillis -> {
                kmView?.text =
                    "+${
                        formatDuration(
                            differenceMillis,
                            roundUp = false
                        )
                    } sobre el tiempo estimado"
                kmView?.setTextColor(
                    Color.rgb(255, 204, 0)
                )
            }

            else -> {
                kmView?.text =
                    "En tiempo"
                kmView?.setTextColor(
                    Color.rgb(125, 211, 252)
                )
            }
        }

        detailsView?.setTextColor(
            Color.LTGRAY
        )
        detailsView?.text =
            "Estimado ${
                formatDuration(
                    timerDurationMillis,
                    roundUp = false
                )
            } · Real ${
                formatDuration(
                    actualMillis,
                    roundUp = false
                )
            }"
    }

    private fun resumeAnalysis() {
        when (mode) {
            Mode.ANALYZING -> Unit

            Mode.TIMER_SETUP -> {
                /*
                 * The countdown never started, so MediaProjection is still
                 * alive. Cancel can return immediately without asking for
                 * screen-capture permission again.
                 */
                mainHandler.removeCallbacks(timerTick)
                mode = Mode.ANALYZING
                onAnalysisPausedChanged(false)
                resetTimerState()
                showWaiting()
            }

            Mode.TIMER_RUNNING,
            Mode.TIMER_FINISHED -> {
                /*
                 * MediaProjection was intentionally stopped when the timer
                 * started. Ask the service to open Android's capture-consent
                 * flow. If the trip was already finalized, the frozen summary
                 * remains visible until permission is granted.
                 */
                onResumeAnalysisRequested()
            }
        }
    }

    /**
     * Called by CaptureService only after Android granted a NEW
     * MediaProjection session and the VirtualDisplay was recreated.
     */
    fun onProjectionResumed() {
        if (
            mode != Mode.TIMER_RUNNING &&
            mode != Mode.TIMER_FINISHED
        ) return

        mainHandler.removeCallbacks(timerTick)
        mode = Mode.ANALYZING
        onAnalysisPausedChanged(false)
        resetTimerState()
        showWaiting()
    }

    private fun resetTimerState() {
        timerStartedElapsed = 0L
        timerEndElapsed = 0L
        timerFinishWallMillis = 0L
        timerDurationMillis = 0L
        timerExpanded = false
    }

    private fun prepareStandardPanel() {
        timerExpanded = false
        restorePanelChrome()
        timerCompactView?.visibility = View.GONE
        hourlyView?.visibility = View.VISIBLE
        kmView?.visibility = View.VISIBLE
        detailsView?.visibility = View.VISIBLE
    }

    private fun restorePanelChrome() {
        root?.apply {
            setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
            )
            background =
                GradientDrawable().apply {
                    setColor(
                        Color.argb(
                            225,
                            24,
                            24,
                            24
                        )
                    )
                    cornerRadius =
                        dp(12).toFloat()
                    setStroke(
                        dp(1),
                        Color.argb(
                            100,
                            255,
                            255,
                            255
                        )
                    )
                }
        }
    }

    private fun hideAnalysisDetails() {
        scoreView?.visibility = View.GONE

        finishEstimateView?.visibility =
            View.GONE
        finishEstimateView?.text = ""

        suggestionsTitleView?.visibility =
            View.GONE

        suggestionsView?.visibility =
            View.GONE
        suggestionsView?.text = ""
    }

    private fun hideTimerControls() {
        timerControls?.visibility = View.GONE
        timerStartView?.visibility = View.GONE
        timerFinishView?.visibility = View.GONE
        timerActionView?.visibility = View.GONE
        timerCompactView?.visibility = View.GONE
    }

    private fun timerAdjustButton(
        label: String,
        onClick: () -> Unit
    ): TextView =
        TextView(context).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            minWidth = dp(48)
            minHeight = dp(44)
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            background =
                GradientDrawable().apply {
                    cornerRadius =
                        dp(9).toFloat()
                    setColor(
                        Color.argb(
                            230,
                            62,
                            62,
                            62
                        )
                    )
                    setStroke(
                        dp(1),
                        Color.argb(
                            100,
                            255,
                            255,
                            255
                        )
                    )
                }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
            }
        }

    private fun actionButton(
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(44)
            setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
            )
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            background =
                GradientDrawable().apply {
                    cornerRadius =
                        dp(9).toFloat()
                    setColor(
                        if (primary) {
                            Color.argb(
                                245,
                                42,
                                130,
                                70
                            )
                        } else {
                            Color.argb(
                                230,
                                62,
                                62,
                                62
                            )
                        }
                    )
                    setStroke(
                        dp(1),
                        Color.argb(
                            100,
                            255,
                            255,
                            255
                        )
                    )
                }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(7)
                }
        }

    private fun formatDuration(
        milliseconds: Long,
        roundUp: Boolean
    ): String {
        val safe =
            milliseconds.coerceAtLeast(0L)

        val seconds =
            if (roundUp) {
                (safe + 999L) / 1000L
            } else {
                safe / 1000L
            }

        val hours =
            seconds / 3600L

        val minutes =
            (seconds % 3600L) / 60L

        val secs =
            seconds % 60L

        return if (hours > 0L) {
            String.format(
                Locale.US,
                "%d:%02d:%02d",
                hours,
                minutes,
                secs
            )
        } else {
            String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                secs
            )
        }
    }

    private fun suggestionLine(offer: RideOffer, targetHourly: Double): String {
        val requiredFare = offer.minimumFareForHourly(targetHourly)
        val delta = requiredFare - offer.fare
        val targetText =
            if (targetHourly % 1.0 == 0.0) targetHourly.toInt().toString()
            else String.format(Locale.US, "%.1f", targetHourly)

        val suffix =
            if (delta > 0.005) String.format(Locale.US, "  +$%.2f", delta)
            else "  ✓ actual"

        return String.format(Locale.US, "%s/h → $%.2f%s", targetText, requiredFare, suffix)
    }

    private fun makeDraggable(
        view: View,
        lp: WindowManager.LayoutParams
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        var lastTapUpAt = 0L

        val dragThreshold =
            dp(8).toFloat()

        view.setOnTouchListener {
                _,
                event ->

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    insideStopZone = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx =
                        event.rawX - touchX
                    val dy =
                        event.rawY - touchY

                    if (
                        !dragging &&
                        hypot(
                            dx.toDouble(),
                            dy.toDouble()
                        ) >= dragThreshold
                    ) {
                        dragging = true
                        lastTapUpAt = 0L
                        showStopTarget()
                    }

                    if (dragging) {
                        lp.x =
                            (startX - dx)
                                .toInt()
                                .coerceAtLeast(0)

                        lp.y =
                            (startY + dy)
                                .toInt()
                                .coerceAtLeast(0)

                        runCatching {
                            windowManager.updateViewLayout(
                                view,
                                lp
                            )
                        }

                        val nowInside =
                            isInsideStopZone(
                                event.rawX,
                                event.rawY
                            )

                        if (
                            nowInside !=
                            insideStopZone
                        ) {
                            insideStopZone =
                                nowInside

                            setStopTargetActive(
                                nowInside
                            )

                            if (nowInside) {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS
                                )
                            }
                        }
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    val wasDragging =
                        dragging

                    val shouldStop =
                        dragging &&
                            isInsideStopZone(
                                event.rawX,
                                event.rawY
                            )

                    hideStopTarget()
                    dragging = false
                    insideStopZone = false

                    if (shouldStop) {
                        view.visibility =
                            View.GONE

                        view.post {
                            remove()
                            onStopRequested()
                        }
                    } else if (
                        !wasDragging &&
                        mode == Mode.TIMER_RUNNING
                    ) {
                        lastTapUpAt = 0L

                        if (timerExpanded) {
                            collapseTimerPanel()
                        } else {
                            expandTimerPanel()
                        }
                    } else if (
                        !wasDragging &&
                        mode == Mode.ANALYZING
                    ) {
                        val now =
                            SystemClock.uptimeMillis()

                        if (
                            lastTapUpAt > 0L &&
                            now - lastTapUpAt <=
                            DOUBLE_TAP_TIMEOUT_MS
                        ) {
                            lastTapUpAt = 0L
                            enterTimerSetup()
                        } else {
                            lastTapUpAt = now
                        }
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    hideStopTarget()
                    dragging = false
                    insideStopZone = false
                    true
                }

                else -> true
            }
        }
    }

    private fun showStopTarget() {
        if (stopTarget != null) return

        val size = dp(STOP_TARGET_SIZE_DP)
        val target = TextView(context).apply {
            text = "✕"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = stopTargetBackground(false)
        }

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(STOP_TARGET_RIGHT_MARGIN_DP)
            y = dp(STOP_TARGET_BOTTOM_MARGIN_DP)
        }

        runCatching { windowManager.addView(target, lp) }
            .onSuccess { stopTarget = target }
    }

    private fun hideStopTarget() {
        stopTarget?.let { runCatching { windowManager.removeViewImmediate(it) } }
        stopTarget = null
    }

    private fun setStopTargetActive(active: Boolean) {
        stopTarget?.apply {
            background = stopTargetBackground(active)
            scaleX = if (active) 1.18f else 1f
            scaleY = if (active) 1.18f else 1f
        }
    }

    private fun stopTargetBackground(active: Boolean) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (active) Color.argb(245, 220, 48, 48)
                else Color.argb(225, 95, 35, 35)
            )
            setStroke(
                dp(if (active) 3 else 2),
                if (active) Color.WHITE else Color.argb(180, 255, 255, 255)
            )
        }

    private fun isInsideStopZone(rawX: Float, rawY: Float): Boolean {
        val (screenWidth, screenHeight) = screenSize()
        val size = dp(STOP_TARGET_SIZE_DP).toFloat()
        val centerX =
            screenWidth - dp(STOP_TARGET_RIGHT_MARGIN_DP) - size / 2f
        val centerY =
            screenHeight - dp(STOP_TARGET_BOTTOM_MARGIN_DP) - size / 2f

        return hypot(
            (rawX - centerX).toDouble(),
            (rawY - centerY).toDouble()
        ) <= size * 0.85f
    }

    private fun screenSize(): Pair<Float, Float> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.maximumWindowMetrics.bounds
            b.width().toFloat() to b.height().toFloat()
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.run {
                widthPixels.toFloat() to heightPixels.toFloat()
            }
        }

    private fun scoreColor(value: Double, excellent: Double, good: Double): Int =
        when {
            value >= excellent -> Color.rgb(76, 217, 100)
            value >= good -> Color.rgb(255, 204, 0)
            else -> Color.rgb(255, 69, 58)
        }

    private fun score100Color(score: Int): Int =
        when {
            score >= 70 -> Color.rgb(76, 217, 100)
            score >= 50 -> Color.rgb(255, 204, 0)
            else -> Color.rgb(255, 69, 58)
        }

    companion object {
        private const val STOP_TARGET_SIZE_DP = 82
        private const val STOP_TARGET_RIGHT_MARGIN_DP = 22
        private const val STOP_TARGET_BOTTOM_MARGIN_DP = 86
        private const val DOUBLE_TAP_TIMEOUT_MS = 420L
        private const val TIMER_TICK_MS = 250L
        private const val FINISH_HOLD_MS = 900L
        private const val COMPACT_TIMER_SIZE_DP = 96
        private const val DEFAULT_TIMER_MINUTES = 10
        private const val MIN_TIMER_MINUTES = 1
        private const val MAX_TIMER_MINUTES = 360

        @Volatile
        private var registeredView: View? = null

        @Volatile
        private var registeredWindowManager: WindowManager? = null

        private fun registerOverlay(wm: WindowManager, view: View) {
            registeredWindowManager = wm
            registeredView = view
        }

        private fun clearRegisteredOverlay(view: View) {
            if (registeredView === view) {
                registeredView = null
                registeredWindowManager = null
            }
        }

        fun removeAnyOverlay(context: Context) {
            val view = registeredView ?: return
            val wm = registeredWindowManager
                ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            runCatching { wm.removeViewImmediate(view) }
            registeredView = null
            registeredWindowManager = null
        }
    }
}
