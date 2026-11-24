package com.jonathan.didiprofit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

internal data class CompactOffer(
    val hourly: String,
    val hourlyGrade: ProfitGrade,
    val distance: String,
    val distanceGrade: ProfitGrade,
    val details: String,
    val target: String,
    val minimum: String
) {
    companion object {
        fun from(offer: RideOffer, thresholds: Thresholds): CompactOffer {
            val target = thresholds.targetHourly1
            val minimum = offer.minimumFareForHourly(target)
            val delta = minimum - offer.fare
            val targetLabel = if (target % 1.0 == 0.0) target.toInt().toString()
                else String.format(Locale.US, "%.1f", target)
            val difference = if (delta > 0.005) String.format(Locale.US, "+$%.2f", delta)
                else "✓ actual"
            return CompactOffer(
                String.format(Locale.US, "$%.0f/h", offer.pesosPerHour),
                ProfitGrade.of(offer.pesosPerHour, thresholds.hourlyExcellent, thresholds.hourlyGood),
                String.format(Locale.US, "$%.2f/km", offer.pesosPerKm),
                ProfitGrade.of(offer.pesosPerKm, thresholds.kmExcellent, thresholds.kmGood),
                String.format(Locale.US, "%d min · %.1f km · Tarifa $%.2f",
                    offer.totalMinutes, offer.totalKilometers, offer.fare),
                "Pon Tu Precio · $targetLabel/h",
                String.format(Locale.US, "Mín. $%.2f   %s", minimum, difference)
            )
        }
    }
}

internal enum class ProfitGrade {
    EXCELLENT, GOOD, LOW;

    companion object {
        fun of(value: Double, excellent: Double, good: Double): ProfitGrade = when {
            value >= excellent -> EXCELLENT
            value >= good -> GOOD
            else -> LOW
        }
    }
}

internal data class WaitingPosition(val x: Float, val y: Float) {
    fun resolve(horizontalTravel: Int, verticalTravel: Int): Pair<Int, Int> =
        pixels(x, horizontalTravel) to pixels(y, verticalTravel)

    companion object {
        fun fromPixels(x: Int, y: Int, horizontalTravel: Int, verticalTravel: Int) =
            WaitingPosition(fraction(x, horizontalTravel), fraction(y, verticalTravel))

        private fun fraction(value: Int, travel: Int): Float =
            if (travel <= 0) 0f else (value.toFloat() / travel).coerceIn(0f, 1f)

        private fun pixels(fraction: Float, travel: Int): Int =
            (fraction.coerceIn(0f, 1f) * travel.coerceAtLeast(0)).roundToInt()
    }
}

internal class CompactOfferPanel(context: Context) : LinearLayout(context) {
    private val hourly = row(22f, bold = true)
    private val distance = row(16f, bold = true)
    private val details = row(10f)
    private val target = row(11f, color = Color.rgb(223, 223, 223))
    private val minimum = row(13f, bold = true)

    init {
        orientation = VERTICAL
        setPadding(dp(11), dp(8), dp(11), dp(9))
        background = GradientDrawable().apply {
            setColor(Color.argb(216, 22, 26, 32))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.argb(128, 221, 233, 249))
        }
        addView(hourly)
        addView(distance)
        addView(details, rowParams(top = 4))
        addView(View(context).apply {
            setBackgroundColor(Color.argb(148, 245, 247, 252))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(8)
            bottomMargin = dp(5)
        })
        addView(target)
        addView(minimum, rowParams(top = 2))
    }

    fun fitToWidth(available: Int) {
        minimumWidth = minOf(dp(156), available)
        val lineWidth = (available - paddingLeft - paddingRight).coerceAtLeast(1)
        for (view in listOf(hourly, distance, details, target, minimum)) {
            if (view.maxWidth != lineWidth) view.maxWidth = lineWidth
        }
    }

    fun render(offer: CompactOffer) {
        hourly.text = offer.hourly
        hourly.setTextColor(color(offer.hourlyGrade))
        distance.text = offer.distance
        distance.setTextColor(color(offer.distanceGrade))
        details.text = offer.details.replace(" min", "\u00A0min").replace(" km", "\u00A0km").replace("Tarifa ", "Tarifa\u00A0")
        target.text = offer.target
        minimum.text = offer.minimum.replace("Mín. ", "Mín.\u00A0")
    }

    private fun row(size: Float, bold: Boolean = false, color: Int = Color.WHITE) =
        TextView(context).apply {
            textSize = size
            includeFontPadding = false
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rowParams(top: Int) = LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun color(grade: ProfitGrade): Int = when (grade) {
        ProfitGrade.EXCELLENT -> Color.rgb(76, 217, 100)
        ProfitGrade.GOOD -> Color.rgb(255, 204, 0)
        ProfitGrade.LOW -> Color.rgb(255, 69, 58)
    }
}

internal class WaitingScannerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val iconColor = Color.rgb(246, 248, 251)
    private val scanLine = LinearGradient(
        17f, 26f, 35f, 26f, intArrayOf(0x00F6F8FB, iconColor, 0x00F6F8FB), null,
        Shader.TileMode.CLAMP
    )

    init {
        contentDescription = context.getString(R.string.overlay_waiting_description)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 52f
        canvas.save()
        canvas.translate((width - 52f * scale) / 2f, (height - 52f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(173, 27, 31, 37)
        canvas.drawCircle(26f, 26f, 25.6f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.65f
        paint.color = Color.argb(153, 245, 248, 252)
        canvas.drawCircle(26f, 26f, 25.6f, paint)

        paint.strokeWidth = 1.8f
        paint.color = iconColor
        for (direction in listOf(-1f, 1f)) {
            for (vertical in listOf(-1f, 1f)) {
                val x = 26f + direction * 10f
                val y = 26f + vertical * 9.5f
                canvas.drawLine(x, y, x - direction * 4f, y, paint)
                canvas.drawLine(x, y, x, y - vertical * 4f, paint)
            }
        }
        paint.shader = scanLine
        canvas.drawLine(17f, 26f, 35f, 26f, paint)
        paint.shader = null
        canvas.restore()
    }
}
