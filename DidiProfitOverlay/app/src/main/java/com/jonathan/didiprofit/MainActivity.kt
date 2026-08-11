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
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var hourlyExcellent: EditText
    private lateinit var hourlyGood: EditText
    private lateinit var kmExcellent: EditText
    private lateinit var kmGood: EditText
    private lateinit var preferences: ProfitPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ProfitPreferences(this)
        setContentView(buildUi())
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            saveThresholds(showToast = false)
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service)
            else startService(service)
            Toast.makeText(this, "Análisis iniciado. Abre DiDi Conductor.", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildUi(): ScrollView {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        content.addView(TextView(this).apply {
            text = "DiDi Rentabilidad"
            textSize = 28f
            setTextColor(Color.rgb(25, 25, 25))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "V1 · Calcula $/hora y $/km incluyendo la recogida y muestra el resultado encima de DiDi Conductor."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(18))
        })

        status = TextView(this).apply {
            textSize = 15f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(242, 242, 242))
        }
        content.addView(status, matchWidth())

        content.addView(spacer(dp(16)))
        content.addView(Button(this).apply {
            text = "1. Permitir superposición"
            setOnClickListener { openOverlayPermission() }
        }, matchWidth())

        content.addView(Button(this).apply {
            text = "2. Iniciar análisis"
            setOnClickListener { startAnalysisFlow() }
        }, matchWidth())

        content.addView(Button(this).apply {
            text = "Detener análisis"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                Toast.makeText(this@MainActivity, "Análisis detenido", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "Semáforo"
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "Verde si alcanza 'excelente', amarillo desde 'bueno' y rojo debajo de ese nivel. Puedes cambiar estos límites."
            setTextColor(Color.DKGRAY)
        })

        val t = preferences.load()
        hourlyExcellent = numberField("$/hora excelente", t.hourlyExcellent)
        hourlyGood = numberField("$/hora bueno", t.hourlyGood)
        kmExcellent = numberField("$/km excelente", t.kmExcellent)
        kmGood = numberField("$/km bueno", t.kmGood)

        content.addView(hourlyExcellent, matchWidth())
        content.addView(hourlyGood, matchWidth())
        content.addView(kmExcellent, matchWidth())
        content.addView(kmGood, matchWidth())

        content.addView(Button(this).apply {
            text = "Guardar límites"
            setOnClickListener { saveThresholds(showToast = true) }
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "Cómo usarla\n\n1. Da permiso de superposición.\n2. Pulsa Iniciar análisis y acepta el aviso de captura de Android (elige DiDi Conductor o toda la pantalla).\n3. Abre DiDi Conductor.\n4. Cuando aparezca una oferta con precio, minutos y distancia de recogida/viaje, el panel flotante mostrará la rentabilidad.\n\nEl panel se puede arrastrar para no tapar los botones de DiDi. La app no acepta ni rechaza viajes automáticamente."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(22), 0, 0)
        })

        return ScrollView(this).apply { addView(content) }
    }

    private fun numberField(label: String, value: Double): EditText = EditText(this).apply {
        hint = label
        setText(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun spacer(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun openOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "El permiso ya está concedido", Toast.LENGTH_SHORT).show()
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
    private fun startAnalysisFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero permite la superposición", Toast.LENGTH_LONG).show()
            openOverlayPermission()
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    private fun saveThresholds(showToast: Boolean) {
        val t = Thresholds(
            hourlyExcellent = hourlyExcellent.text.toString().toDoubleOrNull() ?: 300.0,
            hourlyGood = hourlyGood.text.toString().toDoubleOrNull() ?: 220.0,
            kmExcellent = kmExcellent.text.toString().toDoubleOrNull() ?: 8.0,
            kmGood = kmGood.text.toString().toDoubleOrNull() ?: 6.0
        )
        preferences.save(t)
        if (showToast) Toast.makeText(this, "Límites guardados", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        status.text = if (Settings.canDrawOverlays(this)) {
            "✓ Superposición permitida. Ya puedes iniciar el análisis."
        } else {
            "Falta permiso de superposición. Android te pedirá autorizarlo una sola vez."
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 1001
    }
}
