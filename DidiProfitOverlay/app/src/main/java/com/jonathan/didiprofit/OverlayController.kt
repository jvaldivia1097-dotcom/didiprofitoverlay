package com.jonathan.didiprofit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.hypot

class OverlayController(
    private val context: Context,
    private val onStopRequested: () -> Unit = {}
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var hourlyView: TextView? = null
    private var kmView: TextView? = null
    private var detailsView: TextView? = null
    private var suggestionsTitleView: TextView? = null
    private var suggestionsView: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    // V2.3: drag-to-stop target.
    private var stopTarget: TextView? = null
    private var stopTargetParams: WindowManager.LayoutParams? = null
    private var insideStopZone = false

    private val density get() = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    fun showWaiting() {
        ensureCreated()
        hourlyView?.text = "Analizando DiDi…"
        hourlyView?.setTextColor(Color.WHITE)
        kmView?.text = "Esperando propuesta"
        kmView?.setTextColor(Color.LTGRAY)
        detailsView?.text = "Incluye recogida + viaje"
        suggestionsTitleView?.visibility = View.GONE
        suggestionsView?.visibility = View.GONE
        suggestionsView?.text = ""
    }

    fun showOffer(offer: RideOffer, thresholds: Thresholds) {
        ensureCreated()
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

        suggestionsTitleView?.visibility = View.VISIBLE
        suggestionsView?.visibility = View.VISIBLE
        suggestionsView?.text = listOf(
            suggestionLine(offer, thresholds.targetHourly1),
            suggestionLine(offer, thresholds.targetHourly2),
            suggestionLine(offer, thresholds.targetHourly3)
        ).joinToString("\n")
    }

    fun remove() {
        hideStopTarget()
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        hourlyView = null
        kmView = null
        detailsView = null
        suggestionsTitleView = null
        suggestionsView = null
        params = null
    }

    private fun ensureCreated() {
        if (root != null) return

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

        container.addView(hourlyView)
        container.addView(kmView)
        container.addView(detailsView)
        container.addView(suggestionsTitleView)
        container.addView(suggestionsView)

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
        params = lp
    }

    private fun suggestionLine(offer: RideOffer, targetHourly: Double): String {
        val requiredFare = offer.minimumFareForHourly(targetHourly)
        val delta = requiredFare - offer.fare
        val targetText = if (targetHourly % 1.0 == 0.0) {
            targetHourly.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", targetHourly)
        }
        val suffix = if (delta > 0.005) {
            String.format(Locale.US, "  +$%.2f", delta)
        } else {
            "  ✓ actual"
        }
        return String.format(
            Locale.US,
            "%s/h → $%.2f%s",
            targetText,
            requiredFare,
            suffix
        )
    }

    private fun makeDraggable(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        val dragThreshold = dp(8).toFloat()

        view.setOnTouchListener { _, event ->
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
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY

                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) >= dragThreshold) {
                        dragging = true
                        showStopTarget()
                    }

                    if (dragging) {
                        lp.x = (startX - dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(view, lp) }

                        val nowInside = isInsideStopZone(event.rawX, event.rawY)
                        if (nowInside != insideStopZone) {
                            insideStopZone = nowInside
                            setStopTargetActive(nowInside)
                            if (nowInside) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val shouldStop = dragging && isInsideStopZone(event.rawX, event.rawY)
                    hideStopTarget()
                    dragging = false
                    insideStopZone = false

                    if (shouldStop) {
                        // Post it so the touch event finishes before the overlay/service are removed.
                        view.post { onStopRequested() }
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
            background = stopTargetBackground(active = false)
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
            // Keep it above Android's gesture/navigation area.
            y = dp(STOP_TARGET_BOTTOM_MARGIN_DP)
        }

        runCatching { windowManager.addView(target, lp) }
            .onSuccess {
                stopTarget = target
                stopTargetParams = lp
            }
    }

    private fun hideStopTarget() {
        stopTarget?.let { runCatching { windowManager.removeView(it) } }
        stopTarget = null
        stopTargetParams = null
    }

    private fun setStopTargetActive(active: Boolean) {
        stopTarget?.apply {
            background = stopTargetBackground(active)
            scaleX = if (active) 1.18f else 1.0f
            scaleY = if (active) 1.18f else 1.0f
        }
    }

    private fun stopTargetBackground(active: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (active) {
                    Color.argb(245, 220, 48, 48)
                } else {
                    Color.argb(225, 95, 35, 35)
                }
            )
            setStroke(
                dp(if (active) 3 else 2),
                if (active) Color.WHITE else Color.argb(180, 255, 255, 255)
            )
        }
    }

    private fun isInsideStopZone(rawX: Float, rawY: Float): Boolean {
        val (screenWidth, screenHeight) = screenSize()
        val size = dp(STOP_TARGET_SIZE_DP).toFloat()
        val rightMargin = dp(STOP_TARGET_RIGHT_MARGIN_DP).toFloat()
        val bottomMargin = dp(STOP_TARGET_BOTTOM_MARGIN_DP).toFloat()

        val centerX = screenWidth - rightMargin - size / 2f
        val centerY = screenHeight - bottomMargin - size / 2f

        // Slightly larger hit area than the visible circle.
        val radius = size * 0.85f
        return hypot(
            (rawX - centerX).toDouble(),
            (rawY - centerY).toDouble()
        ) <= radius
    }

    private fun screenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.run {
                widthPixels.toFloat() to heightPixels.toFloat()
            }
        }
    }

    private fun scoreColor(value: Double, excellent: Double, good: Double): Int = when {
        value >= excellent -> Color.rgb(76, 217, 100)
        value >= good -> Color.rgb(255, 204, 0)
        else -> Color.rgb(255, 69, 58)
    }

    companion object {
        private const val STOP_TARGET_SIZE_DP = 82
        private const val STOP_TARGET_RIGHT_MARGIN_DP = 22
        private const val STOP_TARGET_BOTTOM_MARGIN_DP = 86
    }
}
