package com.jonathan.didiprofit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val ocrBusy = AtomicBoolean(false)

    private var lastFrameAt = 0L
    private var lastOfferAt = 0L
    private var lastSignature = ""

    // V2.1: retain the newest frame if OCR is busy or throttled.
    private val pendingLock = Any()
    private var pendingImage: Image? = null
    private var pendingDrainScheduled = false

    private lateinit var overlay: OverlayController
    private lateinit var preferences: ProfitPreferences

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this)
        preferences = ProfitPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (mediaProjection == null) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            @Suppress("DEPRECATION")
            val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (resultCode == Int.MIN_VALUE || resultData == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            startProjection(resultCode, resultData)
        }

        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        overlay.showWaiting()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, mainHandler)

        val density = resources.configuration.densityDpi
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }

        workerThread = HandlerThread("didi-ocr").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).also { reader ->
            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                queueOrProcessImage(image)
            }, workerHandler)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "DidiProfitCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            workerHandler
        )
    }

    private fun queueOrProcessImage(image: Image) {
        val now = System.currentTimeMillis()
        val canProcessNow = !ocrBusy.get() && now - lastFrameAt >= FRAME_INTERVAL_MS

        if (canProcessNow) {
            val stale = synchronized(pendingLock) {
                val old = pendingImage
                pendingImage = null
                old
            }
            stale?.close()

            lastFrameAt = now
            processImage(image)
            return
        }

        synchronized(pendingLock) {
            pendingImage?.close()
            pendingImage = image
        }
        schedulePendingDrain()
    }

    private fun schedulePendingDrain() {
        val handler = workerHandler ?: return
        if (pendingDrainScheduled) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastFrameAt
        val delay = if (ocrBusy.get()) {
            OCR_BUSY_RETRY_MS
        } else {
            maxOf(OCR_BUSY_RETRY_MS, FRAME_INTERVAL_MS - elapsed)
        }

        pendingDrainScheduled = true
        handler.postDelayed({
            pendingDrainScheduled = false
            drainPendingImage()
        }, delay)
    }

    private fun drainPendingImage() {
        val hasPending = synchronized(pendingLock) { pendingImage != null }
        if (!hasPending) return

        if (ocrBusy.get()) {
            schedulePendingDrain()
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastFrameAt
        if (elapsed < FRAME_INTERVAL_MS) {
            schedulePendingDrain()
            return
        }

        val image = synchronized(pendingLock) {
            val latest = pendingImage
            pendingImage = null
            latest
        } ?: return

        lastFrameAt = now
        processImage(image)
    }

    private fun processImage(image: Image) {
        ocrBusy.set(true)
        val bitmap = try {
            imageToBitmap(image)
        } finally {
            image.close()
        }

        if (bitmap == null) {
            ocrBusy.set(false)
            workerHandler?.post { drainPendingImage() }
            return
        }

        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val box = line.boundingBox
                        OcrLine(
                            text = line.text,
                            centerY = box?.centerY() ?: 0,
                            height = box?.height() ?: 0
                        )
                    }
                }.sortedBy { it.centerY }

                val now = System.currentTimeMillis()
                if (OfferParser.isOfferInactive(lines)) {
                    lastOfferAt = 0L
                    lastSignature = ""
                    mainHandler.post { overlay.showWaiting() }
                    return@addOnSuccessListener
                }

                val offer = OfferParser.parse(lines)
                if (offer != null && isPlausible(offer)) {
                    lastOfferAt = now
                    val signature = "${offer.fare}|${offer.totalMinutes}|${"%.3f".format(offer.totalKilometers)}"
                    if (signature != lastSignature) lastSignature = signature
                    mainHandler.post { overlay.showOffer(offer, preferences.load()) }
                } else if (now - lastOfferAt > WAITING_RESET_MS) {
                    mainHandler.post { overlay.showWaiting() }
                }
            }
            .addOnCompleteListener {
                bitmap.recycle()
                ocrBusy.set(false)
                workerHandler?.post { drainPendingImage() }
            }
    }

    private fun isPlausible(offer: RideOffer): Boolean {
        return offer.fare in 10.0..3000.0 &&
            offer.totalMinutes in 2..360 &&
            offer.totalKilometers in 0.2..500.0 &&
            offer.pesosPerHour in 20.0..5000.0 &&
            offer.pesosPerKm in 0.5..500.0
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride

        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (padded !== cropped) padded.recycle()
        return cropped
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Análisis de viajes",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Mantiene activo el análisis de propuestas de DiDi"
                }
            )
        }
    }

    private fun startForegroundCompat() {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
            .setContentTitle("DiDi Rentabilidad activo")
            .setContentText("Analizando propuestas en pantalla")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        imageReader?.setOnImageAvailableListener(null, null)

        synchronized(pendingLock) {
            pendingImage?.close()
            pendingImage = null
        }

        workerHandler?.removeCallbacksAndMessages(null)

        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        runCatching { mediaProjection?.stop() }
        mediaProjection = null

        recognizer.close()
        overlay.remove()

        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.jonathan.didiprofit.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "didi_profit_capture"
        private const val NOTIFICATION_ID = 1042
        private const val FRAME_INTERVAL_MS = 450L
        private const val OCR_BUSY_RETRY_MS = 80L
        private const val WAITING_RESET_MS = 3500L
    }
}
