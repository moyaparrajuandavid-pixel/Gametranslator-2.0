package com.gametranslator.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "GameTranslatorChannel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        createFloatingBubble()
    }

    private fun createFloatingBubble() {
        bubbleView = TextView(this).apply {
            text = "🌐"
            textSize = 28f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xCC1a1a2e.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 300
        }
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        bubbleView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; false }
                MotionEvent.ACTION_MOVE -> { params.x = ix + (e.rawX - tx).toInt(); params.y = iy + (e.rawY - ty).toInt(); windowManager.updateViewLayout(bubbleView, params); true }
                else -> false
            }
        }
        bubbleView.setOnClickListener { showTranslation() }
        windowManager.addView(bubbleView, params)
    }

    private fun showTranslation() {
        val text = AccessibilityTextCapture.getLastCapturedText()
        if (text.isBlank()) {
            Toast.makeText(this, "No se detectó texto en pantalla", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                tryTranslateOnline(text, "es")
            }
            Toast.makeText(this@OverlayService, result, Toast.LENGTH_LONG).show()
        }
    }

    private fun tryTranslateOnline(text: String, target: String): String {
        return try {
            val url = java.net.URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=${java.net.URLEncoder.encode(text, "UTF-8")}")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 10000
            val response = conn.inputStream.bufferedReader().readText()
            val match = Regex("""\[\["([^"]+)"""").find(response)
            match?.groupValues?.get(1) ?: "No se pudo traducir"
        } catch (e: Exception) { "Sin conexión: $text" }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Game Translator", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("🎮 Game Translator activo")
        .setContentText("Toca la burbuja para traducir")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { super.onDestroy(); scope.cancel(); if (::bubbleView.isInitialized) windowManager.removeView(bubbleView) }
    override fun onBind(intent: Intent?): IBinder? = null
                              }
