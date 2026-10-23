package com.jarvis.ai.services

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
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.jarvis.ai.R
import com.jarvis.ai.ai.Gemini
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    companion object {
        val status = MutableStateFlow("OFF")
        var latestFrame: Bitmap? = null
        var timeoutSec = 20
        var running = false
        var current: ScreenCaptureService? = null
        var lastActivity = 0L
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val ui = Handler(Looper.getMainLooper())

    private val checker = object : Runnable {
        override fun run() {
            if (running && System.currentTimeMillis() - lastActivity > timeoutSec * 1000L) {
                stopSelf()
            } else {
                ui.postDelayed(this, 5000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        if (running) return START_STICKY
        val code = intent.getIntExtra("code", 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("data", Intent::class.java) else intent.getParcelableExtra("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        if (Build.VERSION.SDK_INT >= 29)
            startForeground(2, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(2, buildNotif())

        thread = HandlerThread("jarvis-capture").also { it.start() }
        handler = Handler(thread!!.looper)

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        val w = 720; val h = 1280; val dpi = resources.displayMetrics.densityDpi
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ r -> grab(r.acquireLatestImage()) }, handler)
        display = projection?.createVirtualDisplay(
            "jarvis", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )

        running = true
        current = this
        status.value = "ON"
        lastActivity = System.currentTimeMillis()
        ui.postDelayed(checker, 5000)
        return START_STICKY
    }

    private fun grab(image: Image?) {
        image ?: return
        lastActivity = System.currentTimeMillis()
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val ps = plane.pixelStride
            val rs = plane.rowStride
            val w = image.width
            val h = image.height
            val rowPad = rs - ps * w
            val raw = Bitmap.createBitmap(w + rowPad / ps, h, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            val clean = Bitmap.createBitmap(raw, 0, 0, w, h)
            raw.recycle()
            val scaled = Bitmap.createScaledBitmap(clean, 640, (640f * h / w).toInt(), true)
            clean.recycle()
            synchronized(this) { latestFrame?.recycle(); latestFrame = scaled }
        } catch (_: Exception) { } finally { image.close() }
    }

    fun analyze(apiKey: String, model: String, prompt: String): String {
        val bmp = synchronized(this) { latestFrame }
            ?: return "No frame captured yet. Keep the watch running and try again."
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return Gemini.generate(
            apiKey, model,
            "You are JARVIS Vision. Look at the screenshot and answer the user's question about it, briefly. Describe only what is actually visible. Never invent UI elements or content.",
            prompt, b64
        )
    }

    private fun buildNotif(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(NotificationChannel("watch", "Screen Watch", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, "watch")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("JARVIS Screen Watch")
            .setContentText("Screen capture is ON. Stops after ${timeoutSec}s of inactivity.")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        if (current === this) current = null
        status.value = "OFF"
        ui.removeCallbacks(checker)
        try { display?.release(); reader?.close(); projection?.stop() } catch (_: Exception) { }
        thread?.quitSafely()
        super.onDestroy()
    }
}
