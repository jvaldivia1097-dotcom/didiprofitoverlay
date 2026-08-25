package com.jonathan.didiprofit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

enum class AppSection {
    HOME, HISTORY, TOP10, SETTINGS
}

object AppUi {
    val ORANGE = Color.rgb(255, 92, 26)
    val ORANGE_DARK = Color.rgb(224, 72, 8)
    val ORANGE_TINT = Color.rgb(255, 245, 239)

    val GREEN = Color.rgb(35, 176, 80)
    val GREEN_TINT = Color.rgb(238, 249, 241)
    val AMBER = Color.rgb(245, 166, 35)
    val RED = Color.rgb(230, 70, 58)

    val BG = Color.rgb(247, 248, 250)
    val SURFACE = Color.WHITE
    val TEXT = Color.rgb(24, 28, 36)
    val MUTED = Color.rgb(103, 111, 124)
    val BORDER = Color.rgb(229, 232, 237)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(
        context: Context,
        color: Int,
        radiusDp: Int = 18,
        strokeColor: Int? = null,
        strokeDp: Int = 1
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null) {
            setStroke(dp(context, strokeDp), strokeColor)
        }
    }

    fun card(
        context: Context,
        tint: Int = SURFACE,
        paddingDp: Int = 16
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dp(context, paddingDp),
            dp(context, paddingDp),
            dp(context, paddingDp),
            dp(context, paddingDp)
        )
        background = rounded(context, tint, 18, BORDER)
        elevation = dp(context, 1).toFloat()
    }

    fun title(
        context: Context,
        textValue: String,
        size: Float = 24f
    ) = TextView(context).apply {
        text = textValue
        textSize = size
        setTextColor(TEXT)
        typeface = Typeface.DEFAULT_BOLD
    }

    fun body(
        context: Context,
        textValue: String,
        size: Float = 14f,
        color: Int = MUTED
    ) = TextView(context).apply {
        text = textValue
        textSize = size
        setTextColor(color)
    }

    fun sectionTitle(
        context: Context,
        textValue: String
    ) = TextView(context).apply {
        text = textValue
        textSize = 19f
        setTextColor(TEXT)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(context, 22), 0, dp(context, 9))
    }

    fun primaryButton(
        context: Context,
        textValue: String,
        onClick: () -> Unit
    ) = Button(context).apply {
        text = textValue
        textSize = 16f
        isAllCaps = false
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(context, ORANGE, 16)
        stateListAnimator = null
        minHeight = dp(context, 54)
        setOnClickListener { onClick() }
    }

    fun secondaryButton(
        context: Context,
        textValue: String,
        onClick: () -> Unit
    ) = Button(context).apply {
        text = textValue
        textSize = 15f
        isAllCaps = false
        setTextColor(TEXT)
        background = rounded(context, SURFACE, 16, BORDER)
        stateListAnimator = null
        minHeight = dp(context, 50)
        setOnClickListener { onClick() }
    }

    fun dangerButton(
        context: Context,
        textValue: String,
        onClick: () -> Unit
    ) = Button(context).apply {
        text = textValue
        textSize = 15f
        isAllCaps = false
        setTextColor(RED)
        background = rounded(context, SURFACE, 16, RED)
        stateListAnimator = null
        minHeight = dp(context, 48)
        setOnClickListener { onClick() }
    }

    fun numberField(
        context: Context,
        value: Double,
        hintText: String
    ) = EditText(context).apply {
        hint = hintText
        setText(
            if (value % 1.0 == 0.0) value.toInt().toString()
            else value.toString()
        )
        textSize = 17f
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        setPadding(
            dp(context, 13),
            dp(context, 10),
            dp(context, 13),
            dp(context, 10)
        )
        inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        background = rounded(context, Color.WHITE, 13, BORDER)
    }

    fun metricTile(
        context: Context,
        value: String,
        label: String,
        valueColor: Int = TEXT
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(
            dp(context, 8),
            dp(context, 11),
            dp(context, 8),
            dp(context, 11)
        )
        background = rounded(context, Color.rgb(250, 250, 251), 14, BORDER)

        addView(TextView(context).apply {
            text = value
            textSize = 20f
            setTextColor(valueColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 2), 0, 0)
        })
    }

    fun segment(
        context: Context,
        textValue: String,
        selected: Boolean,
        onClick: () -> Unit
    ) = TextView(context).apply {
        text = textValue
        gravity = Gravity.CENTER
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (selected) Color.WHITE else MUTED)
        setPadding(
            dp(context, 8),
            dp(context, 10),
            dp(context, 8),
            dp(context, 10)
        )
        background = rounded(
            context,
            if (selected) ORANGE else Color.TRANSPARENT,
            14,
            if (selected) ORANGE else BORDER
        )
        setOnClickListener { onClick() }
    }

    fun bottomNav(
        activity: Activity,
        selected: AppSection
    ): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                dp(activity, 8),
                dp(activity, 6),
                dp(activity, 8),
                dp(activity, 7)
            )
            setBackgroundColor(Color.WHITE)
            elevation = dp(activity, 9).toFloat()
        }

        data class Item(
            val section: AppSection,
            val icon: String,
            val label: String,
            val target: Class<out Activity>
        )

        val items = listOf(
            Item(AppSection.HOME, "⌂", "Inicio", MainActivity::class.java),
            Item(AppSection.HISTORY, "◷", "Historial", HistoryActivity::class.java),
            Item(AppSection.TOP10, "★", "Top 10", TopTenActivity::class.java),
            Item(AppSection.SETTINGS, "⚙", "Ajustes", SettingsActivity::class.java)
        )

        items.forEach { item ->
            val active = item.section == selected
            val view = TextView(activity).apply {
                text = "${item.icon}\n${item.label}"
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (active) ORANGE else MUTED)
                typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(
                    dp(activity, 2),
                    dp(activity, 5),
                    dp(activity, 2),
                    dp(activity, 3)
                )
                if (!active) {
                    setOnClickListener {
                        val intent = Intent(activity, item.target)
                        if (item.section == AppSection.HOME) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        activity.startActivity(intent)
                    }
                }
            }
            row.addView(
                view,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        return row
    }

    fun fullWidth(
        context: Context,
        topDp: Int = 0,
        bottomDp: Int = 0
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(0, dp(context, topDp), 0, dp(context, bottomDp))
    }

    fun scoreColor(score: Int): Int = when {
        score >= 70 -> GREEN
        score >= 50 -> AMBER
        else -> RED
    }
}
