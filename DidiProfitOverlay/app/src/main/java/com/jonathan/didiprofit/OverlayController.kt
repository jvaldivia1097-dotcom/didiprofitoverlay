package com.jonathan.didiprofit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var hourlyView: TextView? = null
    private var kmView: TextView? = null
    private var detailsView: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun showWaiting() {
        ensureCreated()
        hourlyView?.text = "Analizando DiDi…"
        hourlyView?.setTextColor(Color.WHITE)
        kmView?.text = "Esperando propuesta"
        kmView?.setTextColor(Color.LTGRAY)
        detailsView?.text = "Incluye recogida + viaje"
    }

    fun showOffer(offer: RideOffer, thresholds: Thresholds) {
        ensureCreated()
        hourlyView?.text = String.format(Locale.US, "$%.0f/h", offer.pesosPerHour)
        hourlyView?.setTextColor(scoreColor(offer.pesosPerHour, thresholds.hourlyExcellent, thresholds.hourlyGood))

        kmView?.text = String.format(Locale.US, "$%.2f/km", offer.pesosPerKm)
        kmView?.setTextColor(scoreColor(offer.pesosPerKm, thresholds.kmExcellent, thresholds.kmGood))

        detailsView?.text = String.format(
            Locale.US,
            "%d min · %.1f km · tarifa %.2f",
            offer.totalMinutes,
            offer.totalKilometers,
            offer.fare
        )
    }

    fun remove() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        hourlyView = null
        kmView = null
        detailsView = null
        params = null
    }

    private fun ensureCreated() {
        if (root != null) return

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

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

        container.addView(hourlyView)
        container.addView(kmView)
        container.addView(detailsView)

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

    private fun makeDraggable(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Gravity.END means positive x moves inward from the right edge.
                    lp.x = (startX - (event.rawX - touchX)).toInt().coerceAtLeast(0)
                    lp.y = (startY + (event.rawY - touchY)).toInt().coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(view, lp) }
                    true
                }
                else -> true
            }
        }
    }

    private fun scoreColor(value: Double, excellent: Double, good: Double): Int = when {
        value >= excellent -> Color.rgb(76, 217, 100)
        value >= good -> Color.rgb(255, 204, 0)
        else -> Color.rgb(255, 69, 58)
    }
}
