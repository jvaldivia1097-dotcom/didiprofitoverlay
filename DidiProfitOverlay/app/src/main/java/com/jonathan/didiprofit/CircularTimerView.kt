package com.jonathan.didiprofit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Compact trip timer used while a ride is in progress.
 *
 * Normal countdown:
 * - dark circular body
 * - cyan progress ring fills clockwise as the estimate is consumed
 * - only the remaining time is shown
 *
 * Overtime:
 * - the whole circle changes to yellow
 * - the ring remains completely filled
 * - text becomes +MM:SS
 */
internal class CircularTimerView(
    context: Context
) : View(context) {

    private val density =
        context.resources.displayMetrics.density

    private val scaledDensity =
        context.resources.displayMetrics.scaledDensity

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 5f * density
        }

    private val progressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 5f * density
        }

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

    private val arcRect = RectF()

    private var displayText = "00:00"
    private var progress = 0f
    private var overtime = false

    fun setTimerState(
        text: String,
        progressFraction: Float,
        isOvertime: Boolean
    ) {
        displayText = text
        progress =
            progressFraction.coerceIn(0f, 1f)
        overtime = isOvertime
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val stroke = progressPaint.strokeWidth
        val radius =
            min(width, height) / 2f -
                stroke / 2f -
                2f * density

        if (radius <= 0f) return

        if (overtime) {
            fillPaint.color =
                Color.rgb(255, 204, 0)

            trackPaint.color =
                Color.rgb(255, 224, 94)

            progressPaint.color =
                Color.rgb(255, 235, 145)

            textPaint.color =
                Color.rgb(35, 35, 35)
        } else {
            fillPaint.color =
                Color.argb(238, 24, 24, 24)

            trackPaint.color =
                Color.argb(105, 255, 255, 255)

            progressPaint.color =
                Color.rgb(125, 211, 252)

            textPaint.color =
                Color.WHITE
        }

        canvas.drawCircle(
            cx,
            cy,
            radius,
            fillPaint
        )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            trackPaint
        )

        arcRect.set(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        val sweep =
            if (overtime) {
                360f
            } else {
                360f * progress
            }

        if (sweep > 0f) {
            canvas.drawArc(
                arcRect,
                -90f,
                sweep,
                false,
                progressPaint
            )
        }

        textPaint.textSize =
            when {
                displayText.length >= 8 ->
                    16f * scaledDensity
                displayText.length >= 6 ->
                    18f * scaledDensity
                else ->
                    20f * scaledDensity
            }

        val baseline =
            cy -
                (
                    textPaint.ascent() +
                        textPaint.descent()
                ) / 2f

        canvas.drawText(
            displayText,
            cx,
            baseline,
            textPaint
        )
    }
}
