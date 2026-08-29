package com.jonathan.didiprofit

import android.app.KeyguardManager
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
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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

    @Volatile private var isStopping = false
    @Volatile private var explicitStopRequested = false
    @Volatile private var analysisPaused = false
    private var lastFrameAt = 0L
    private var lastOfferAt = 0L
    private val historyCandidateTracker = HistoryCandidateTracker()

    private val pendingLock = Any()
    private var pendingImage: Image? = null
    private var pendingDrainScheduled = false

    private lateinit var overlay: OverlayController
    private lateinit var preferences: ProfitPreferences
    private lateinit var history: HistoryRepository

    override fun onCreate() {
        super.onCreate()
        OverlayController.removeAnyOverlay(this)
        overlay = OverlayController(
            context = this,
            onAnalysisPausedChanged = { paused -> setAnalysisPaused(paused) },
            onStopRequested = { requestStop(userInitiated = true) }
        )
        preferences = ProfitPreferences(this)
        history = HistoryRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop(userInitiated = true)
            return START_NOT_STICKY
        }
        if (isStopping) return START_NOT_STICKY

        startForegroundCompat()

        if (!Settings.canDrawOverlays(this)) {
            requestStop(userInitiated = false)
            return START_NOT_STICKY
        }

        if (mediaProjection == null) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            @Suppress("DEPRECATION")
            val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (resultCode == Int.MIN_VALUE || resultData == null) {
                requestStop(userInitiated = false)
                return START_NOT_STICKY
            }
            startProjection(resultCode, resultData)
        }
        return START_NOT_STICKY
    }

    private fun requestStop(
        userInitiated: Boolean,
        preserveResumeState: Boolean = false
    ) {
        if (isStopping) return

        if (userInitiated) {
            explicitStopRequested = true
            AnalysisResumeState.clear(this)
        } else if (!preserveResumeState) {
            AnalysisResumeState.cancelNotification(this)
        }

        isStopping = true
        analysisPaused = true
        mainHandler.removeCallbacksAndMessages(null)
        historyCandidateTracker.reset()
        if (::overlay.isInitialized) overlay.remove()
        OverlayController.removeAnyOverlay(this)

        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun postOverlay(action: () -> Unit) {
        if (isStopping || analysisPaused) return
        mainHandler.post {
            if (!isStopping && !analysisPaused) action()
        }
    }

    /**
     * V3.6.0: the manual trip timer owns the overlay while it is active.
     * Pausing here also stops OCR/history work so navigation screens cannot
     * overwrite the timer or accidentally create history entries.
     */
    private fun setAnalysisPaused(paused: Boolean) {
        if (isStopping || analysisPaused == paused) return
        analysisPaused = paused
        historyCandidateTracker.reset()

        synchronized(pendingLock) {
            pendingImage?.close()
            pendingImage = null
            pendingDrainScheduled = false
        }

        if (!paused) {
            lastFrameAt = 0L
            lastOfferAt = 0L
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (isStopping) return
        overlay.showWaiting()

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection

        AnalysisResumeState.clear(this)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handleProjectionStoppedBySystem()
            }
        }, mainHandler)

        val density = resources.configuration.densityDpi
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.run { widthPixels to heightPixels }
        }

        workerThread = HandlerThread("didi-ocr").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).also { reader ->
            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (isStopping || analysisPaused) {
                    image.close()
                } else {
                    queueOrProcessImage(image)
                }
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

    private fun handleProjectionStoppedBySystem() {
        if (isStopping || explicitStopRequested) return

        val reason = if (isDeviceLocked()) {
            AnalysisResumeState.REASON_SCREEN_LOCK
        } else {
            AnalysisResumeState.REASON_PROJECTION_STOPPED
        }

        AnalysisResumeState.markPending(this, reason)
        AnalysisResumeState.postResumeNotification(this, reason)
        requestStop(userInitiated = false, preserveResumeState = true)
    }

    private fun isDeviceLocked(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !powerManager.isInteractive || keyguardManager.isKeyguardLocked
    }

    private fun queueOrProcessImage(image: Image) {
        if (isStopping || analysisPaused) {
            image.close()
            return
        }

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
        if (isStopping || analysisPaused) return
        val handler = workerHandler ?: return
        if (pendingDrainScheduled) return

        val elapsed = System.currentTimeMillis() - lastFrameAt
        val delay = if (ocrBusy.get()) OCR_BUSY_RETRY_MS
        else maxOf(OCR_BUSY_RETRY_MS, FRAME_INTERVAL_MS - elapsed)

        pendingDrainScheduled = true
        handler.postDelayed({
            pendingDrainScheduled = false
            if (!isStopping && !analysisPaused) drainPendingImage()
        }, delay)
    }

    private fun drainPendingImage() {
        if (isStopping || analysisPaused) return
        if (synchronized(pendingLock) { pendingImage == null }) return

        if (ocrBusy.get()) {
            schedulePendingDrain()
            return
        }

        val elapsed = System.currentTimeMillis() - lastFrameAt
        if (elapsed < FRAME_INTERVAL_MS) {
            schedulePendingDrain()
            return
        }

        val image = synchronized(pendingLock) {
            val latest = pendingImage
            pendingImage = null
            latest
        } ?: return

        lastFrameAt = System.currentTimeMillis()
        processImage(image)
    }

    /**
     * V3.1 OCR strategy:
     * 1) OCR only the lower 75% where DiDi offer cards live. This cuts map/navigation noise.
     * 2) If that pass cannot construct a proposal, retry once using the full screen.
     *
     * Successful offers normally need only the faster first pass.
     */
    private fun processImage(image: Image) {
        if (isStopping || analysisPaused) {
            image.close()
            return
        }

        ocrBusy.set(true)
        val fullBitmap = try {
            imageToBitmap(image)
        } finally {
            image.close()
        }

        if (fullBitmap == null) {
            finishOcr()
            return
        }

        val primary = createOfferRegion(fullBitmap)
        runRecognition(
            bitmap = primary,
            fullBitmap = fullBitmap,
            isPrimary = primary !== fullBitmap
        )
    }

    private fun runRecognition(
        bitmap: Bitmap,
        fullBitmap: Bitmap,
        isPrimary: Boolean
    ) {
        if (isStopping || analysisPaused) {
            if (bitmap !== fullBitmap && !bitmap.isRecycled) bitmap.recycle()
            if (!fullBitmap.isRecycled) fullBitmap.recycle()
            finishOcr()
            return
        }

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (isStopping || analysisPaused) {
                    if (bitmap !== fullBitmap && !bitmap.isRecycled) bitmap.recycle()
                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                    finishOcr()
                    return@addOnSuccessListener
                }

                val lines = extractLines(result)
                val handled = handleRecognizedLines(lines, finalAttempt = !isPrimary)

                if (handled) {
                    if (bitmap !== fullBitmap && !bitmap.isRecycled) bitmap.recycle()
                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                    finishOcr()
                } else if (isPrimary) {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    runRecognition(
                        bitmap = fullBitmap,
                        fullBitmap = fullBitmap,
                        isPrimary = false
                    )
                } else {
                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                    finishOcr()
                }
            }
            .addOnFailureListener {
                if (bitmap !== fullBitmap && !bitmap.isRecycled) bitmap.recycle()

                if (isPrimary && !isStopping && !analysisPaused) {
                    runRecognition(
                        bitmap = fullBitmap,
                        fullBitmap = fullBitmap,
                        isPrimary = false
                    )
                } else {
                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                    finishOcr()
                }
            }
    }

    private fun extractLines(result: Text): List<OcrLine> =
        result.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(line.text, box?.centerY() ?: 0, box?.height() ?: 0)
            }
        }.sortedBy { it.centerY }

    /**
     * Returns true when the OCR result already produced a definitive state
     * (valid offer or inactive-offer message). On the primary ROI pass, a miss
     * returns false so the full-screen fallback can run before showing "waiting".
     */
    private fun handleRecognizedLines(lines: List<OcrLine>, finalAttempt: Boolean): Boolean {
        if (analysisPaused) return true
        val now = System.currentTimeMillis()

        if (OfferParser.isOfferInactive(lines)) {
            lastOfferAt = 0L
            historyCandidateTracker.reset()
            postOverlay { overlay.showWaiting() }
            return true
        }

        val offer = OfferParser.parse(lines)
        if (offer != null && isPlausible(offer)) {
            lastOfferAt = now
            val thresholds = preferences.load()

            /*
             * V3.5.1:
             * The overlay remains instant, exactly as before.
             * History persistence waits for 3 stable consecutive parsed reads.
             * No frames are merged and the OCR/parser output is not modified.
             */
            val confirmedForHistory = historyCandidateTracker.observe(offer)
            if (confirmedForHistory != null) {
                workerHandler?.post {
                    if (!isStopping && !analysisPaused) {
                        history.recordOrMerge(
                            confirmedForHistory,
                            thresholds
                        )
                    }
                }
            }

            postOverlay {
                overlay.showOffer(
                    offer,
                    thresholds
                )
            }
            return true
        }

        if (finalAttempt) {
            // A final OCR miss breaks the "consecutive stable reads" sequence.
            historyCandidateTracker.reset()

            if (now - lastOfferAt > WAITING_RESET_MS) {
                postOverlay { overlay.showWaiting() }
            }
        }

        return false
    }

    private fun finishOcr() {
        ocrBusy.set(false)
        if (!isStopping && !analysisPaused) workerHandler?.post { drainPendingImage() }
    }

    private fun isPlausible(offer: RideOffer): Boolean =
        offer.fare in 10.0..3000.0 &&
            offer.totalMinutes in 2..360 &&
            offer.totalKilometers in 0.2..500.0 &&
            offer.pesosPerHour in 20.0..5000.0 &&
            offer.pesosPerKm in 0.5..500.0

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

        // Remove only the status bar.
        val top = (cropped.height * 0.035).toInt().coerceAtLeast(0)
        val content = if (top > 0 && cropped.height - top > 100) {
            Bitmap.createBitmap(cropped, 0, top, cropped.width, cropped.height - top).also {
                if (it !== cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        // Keep enough detail for small "m/km" text while reducing OCR cost.
        if (content.width > 1080) {
            val newHeight = (content.height * (1080.0 / content.width)).toInt()
            return Bitmap.createScaledBitmap(content, 1080, newHeight, true).also {
                if (it !== content) content.recycle()
            }
        }
        return content
    }

    private fun createOfferRegion(full: Bitmap): Bitmap {
        if (full.height < 400) return full
        val top = (full.height * PRIMARY_REGION_TOP_RATIO).toInt()
            .coerceIn(0, full.height - 200)

        if (top <= 0) return full
        return Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
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
            @Suppress("DEPRECATION")
            Notification.Builder(this)
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
        isStopping = true
        analysisPaused = true
        historyCandidateTracker.reset()
        mainHandler.removeCallbacksAndMessages(null)

        if (::overlay.isInitialized) overlay.remove()
        OverlayController.removeAnyOverlay(this)

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
        runCatching { recognizer.close() }

        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null

        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.jonathan.didiprofit.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "didi_profit_capture"
        private const val NOTIFICATION_ID = 1042

        // Faster reaction than V3 while still keeping one latest pending frame.
        private const val FRAME_INTERVAL_MS = 320L
        private const val OCR_BUSY_RETRY_MS = 60L
        private const val WAITING_RESET_MS = 3500L

        // First OCR pass starts at 25% of the usable screenshot height.
        private const val PRIMARY_REGION_TOP_RATIO = 0.25
    }
}
