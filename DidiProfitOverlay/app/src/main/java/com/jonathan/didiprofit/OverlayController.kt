package com.jonathan.didiprofit

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
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
    private var waitingView: WaitingScannerView? = null
    private var offerPanel: CompactOfferPanel? = null
    private var renderedOffer: CompactOffer? = null
    private var positionParams: WindowManager.LayoutParams? = null
    private val positionPreferences =
        context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE)
    private var waitingPosition: WaitingPosition? =
        if (positionPreferences.contains("waiting_x") && positionPreferences.contains("waiting_y")) {
            WaitingPosition(
                positionPreferences.getFloat("waiting_x", 0f),
                positionPreferences.getFloat("waiting_y", 0f)
            )
        } else null
    private var gestureActive = false
    private var lastTapUpAt = 0L
    private var displayArea = Rect()
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
        if (isWaiting()) return

        cancelGesture()
        renderedOffer = null
        offerPanel?.visibility = View.GONE
        setTimerHeaderVisibility(View.GONE)
        hideTimerControls()
        root?.setPadding(0, 0, 0, 0)
        root?.background = null
        waitingView?.visibility = View.VISIBLE
        applyAnalysisPlacement()
    }

    fun showOffer(offer: RideOffer, thresholds: Thresholds) {
        if (mode != Mode.ANALYZING) return
        ensureCreated()
        lastOfferMinutes = offer.totalMinutes.coerceIn(MIN_TIMER_MINUTES, MAX_TIMER_MINUTES)
        val presentation = CompactOffer.from(offer, thresholds)
        if (presentation == renderedOffer) return

        offerPanel?.render(presentation)
        val entering = renderedOffer == null
        renderedOffer = presentation
        if (!entering) return

        cancelGesture()
        waitingView?.visibility = View.GONE
        setTimerHeaderVisibility(View.GONE)
        hideTimerControls()
        root?.setPadding(0, 0, 0, 0)
        root?.background = null
        offerPanel?.visibility = View.VISIBLE
        applyAnalysisPlacement()
    }
    fun remove() {
        mainHandler.removeCallbacks(timerTick)
        cancelGesture()
        val view = root
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
            clearRegisteredOverlay(view)
        }
        root = null
        hourlyView = null
        kmView = null
        detailsView = null
        waitingView = null
        offerPanel = null
        renderedOffer = null
        positionParams = null
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

        val container = object : LinearLayout(context) {
            override fun onConfigurationChanged(newConfig: Configuration) {
                super.onConfigurationChanged(newConfig)
                post {
                    if (root === this) updateDisplayArea()
                }
            }
        }.apply {
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
        waitingView = WaitingScannerView(context).apply { visibility = View.GONE }
        offerPanel = CompactOfferPanel(context).apply { visibility = View.GONE }
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
        container.addView(waitingView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))
        container.addView(offerPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
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
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        }

        makeDraggable(container, lp)
        container.addOnLayoutChangeListener { _, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom ->
            if (mode != Mode.ANALYZING &&
                (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
            ) {
                container.post {
                    if (root === container && mode != Mode.ANALYZING) moveWithinDisplay(lp.x, lp.y)
                }
            }
        }
        container.setOnApplyWindowInsetsListener { _, insets ->
            container.post { if (root === container) updateDisplayArea() }
            insets
        }
        displayArea = usableDisplay()
        initialWaitingPlacement(lp)
        windowManager.addView(container, lp)
        root = container
        positionParams = lp
        registerOverlay(windowManager, container)
    }

    private fun enterTimerSetup() {
        if (mode != Mode.ANALYZING) return

        ensureCreated()
        mode = Mode.TIMER_SETUP
        prepareTimerSetupPanel()
        mainHandler.removeCallbacks(timerTick)
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

    private fun prepareTimerSetupPanel() {
        cancelGesture()
        renderedOffer = null
        waitingView?.visibility = View.GONE
        offerPanel?.visibility = View.GONE
        timerExpanded = false
        restorePanelChrome()
        timerCompactView?.visibility = View.GONE
        setTimerHeaderVisibility(View.VISIBLE)
        setWindowGeometry(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, positionParams?.x ?: 0, positionParams?.y ?: 0)
    }

    private fun setTimerHeaderVisibility(visibility: Int) {
        hourlyView?.visibility = visibility
        kmView?.visibility = visibility
        detailsView?.visibility = visibility
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

    private fun isWaiting() = waitingView?.visibility == View.VISIBLE

    private fun usableDisplay(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return Rect(metrics.bounds).apply {
                left += bars.left
                top += bars.top
                right -= bars.right
                bottom -= bars.bottom
            }
        }
        @Suppress("DEPRECATION")
        val frame = Rect().apply { root?.getWindowVisibleDisplayFrame(this) }
        if (frame.width() > 0 && frame.height() > 0) return frame
        return context.resources.displayMetrics.run { Rect(0, 0, widthPixels, heightPixels) }
    }

    private fun updateDisplayArea() {
        val available = usableDisplay()
        if (available == displayArea) return
        displayArea = available
        cancelGesture()
        if (mode == Mode.ANALYZING) {
            applyAnalysisPlacement()
        } else {
            positionParams?.let { moveWithinDisplay(it.x, it.y) }
        }
    }

    private fun waitingSize() = dp(52).coerceAtMost(
        minOf(displayArea.width(), displayArea.height()).coerceAtLeast(1)
    )

    private fun waitingCoordinates(size: Int): Pair<Int, Int> {
        val travelX = (displayArea.width() - size).coerceAtLeast(0)
        val travelY = (displayArea.height() - size).coerceAtLeast(0)
        val (x, y) = waitingPosition?.resolve(travelX, travelY)
            ?: ((travelX - dp(12)).coerceAtLeast(0) to dp(150).coerceAtMost(travelY))
        return (displayArea.left + x) to (displayArea.top + y)
    }

    private fun initialWaitingPlacement(lp: WindowManager.LayoutParams) {
        val size = waitingSize()
        val (x, y) = waitingCoordinates(size)
        lp.width = size
        lp.height = size
        lp.x = x
        lp.y = y
    }

    private fun applyAnalysisPlacement() {
        if (isWaiting()) {
            val size = waitingSize()
            val (x, y) = waitingCoordinates(size)
            setWindowGeometry(size, size, x, y)
        } else if (renderedOffer != null) {
            val margin = dp(4)
            offerPanel?.fitToWidth((displayArea.width() - margin * 2).coerceAtLeast(1))
            setWindowGeometry(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                displayArea.left + margin, displayArea.top + margin)
        }
    }

    private fun setWindowGeometry(width: Int, height: Int, x: Int, y: Int) {
        val lp = positionParams ?: return
        val view = root ?: return
        if (lp.width == width && lp.height == height && lp.x == x && lp.y == y) return
        lp.width = width
        lp.height = height
        lp.x = x
        lp.y = y
        windowManager.updateViewLayout(view, lp)
    }

    private fun moveWithinDisplay(x: Int, y: Int) {
        val lp = positionParams ?: return
        val view = root ?: return
        val width = if (isWaiting()) lp.width else view.width
        val height = if (isWaiting()) lp.height else view.height
        setWindowGeometry(lp.width, lp.height,
            x.coerceIn(displayArea.left, (displayArea.right - width).coerceAtLeast(displayArea.left)),
            y.coerceIn(displayArea.top, (displayArea.bottom - height).coerceAtLeast(displayArea.top)))
    }

    private fun saveWaitingPosition(lp: WindowManager.LayoutParams) {
        val position = WaitingPosition.fromPixels(
            lp.x - displayArea.left, lp.y - displayArea.top,
            displayArea.width() - lp.width, displayArea.height() - lp.height
        )
        waitingPosition = position
        positionPreferences.edit()
            .putFloat("waiting_x", position.x)
            .putFloat("waiting_y", position.y)
            .apply()
    }

    private fun cancelGesture() {
        gestureActive = false
        lastTapUpAt = 0L
        insideStopZone = false
        hideStopTarget()
    }

    private fun makeDraggable(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isWaiting() &&
                        hypot(event.x - view.width / 2f, event.y - view.height / 2f) > view.width / 2f
                    ) {
                        cancelGesture()
                        return@setOnTouchListener false
                    }
                    gestureActive = true
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    insideStopZone = false
                }

                MotionEvent.ACTION_MOVE -> if (gestureActive) {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    // During an offer the panel stays in its reading position.
                    if (!(mode == Mode.ANALYZING && !isWaiting())) {
                        if (!dragging && hypot(dx, dy) >= dp(8)) {
                            dragging = true
                            lastTapUpAt = 0L
                            showStopTarget()
                        }
                        if (dragging) {
                            moveWithinDisplay((startX + dx).toInt(), (startY + dy).toInt())
                            val inside = isInsideStopZone(event.rawX, event.rawY)
                            if (inside != insideStopZone) {
                                insideStopZone = inside
                                setStopTargetActive(inside)
                                if (inside) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        }
                    } else if (hypot(dx, dy) >= dp(8)) {
                        // Suppress a tap without moving the offer or showing the stop target.
                        dragging = true
                        lastTapUpAt = 0L
                    }
                }

                MotionEvent.ACTION_UP -> if (gestureActive) {
                    gestureActive = false
                    val shouldStop = dragging && stopTarget != null &&
                        isInsideStopZone(event.rawX, event.rawY)
                    hideStopTarget()
                    insideStopZone = false
                    when {
                        shouldStop -> {
                            remove()
                            onStopRequested()
                        }
                        dragging && isWaiting() -> saveWaitingPosition(lp)
                        !dragging && mode == Mode.TIMER_RUNNING -> {
                            if (timerExpanded) collapseTimerPanel() else expandTimerPanel()
                        }
                        !dragging && mode == Mode.ANALYZING -> {
                            val now = SystemClock.uptimeMillis()
                            if (lastTapUpAt > 0L && now - lastTapUpAt <= DOUBLE_TAP_TIMEOUT_MS) {
                                enterTimerSetup()
                            } else {
                                lastTapUpAt = now
                            }
                        }
                    }
                }

                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    val restorePosition = gestureActive && dragging && isWaiting()
                    cancelGesture()
                    if (restorePosition) applyAnalysisPlacement()
                }
            }
            true
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
