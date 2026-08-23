package com.jonathan.didiprofit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AnalysisResumeState {
    const val ACTION_RESUME_ANALYSIS = "com.jonathan.didiprofit.RESUME_ANALYSIS"

    private const val PREFS = "analysis_resume_state"
    private const val KEY_PENDING = "resume_pending"
    private const val KEY_REASON = "resume_reason"

    private const val CHANNEL_ID = "didi_profit_resume"
    private const val NOTIFICATION_ID = 1043

    const val REASON_SCREEN_LOCK = "screen_lock"
    const val REASON_PROJECTION_STOPPED = "projection_stopped"

    fun markPending(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_REASON, reason)
            .apply()
    }

    fun isPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING, false)

    fun reason(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REASON, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        cancelNotification(context)
    }

    fun postResumeNotification(context: Context, reason: String) {
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reanudar análisis",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Avisos cuando Android detiene la captura de pantalla"
                }
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_RESUME_ANALYSIS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1043,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (reason == REASON_SCREEN_LOCK) {
            "Android pausó la captura al bloquear la pantalla. Toca para reanudar."
        } else {
            "La captura de pantalla se detuvo. Toca para reanudar el análisis."
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        manager.notify(
            NOTIFICATION_ID,
            builder
                .setContentTitle("DiDi Rentabilidad pausado")
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }
}
