package com.example.reeltoshorts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: Button
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForeground(1001, createNotification())
        windowManager = getSystemService(WindowManager::class.java)

        val inflater = LayoutInflater.from(this)
        floatingButton = inflater.inflate(R.layout.overlay_button, null) as Button
        floatingButton.setOnClickListener {
            handleButtonClick()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 30
        params.y = 260

        windowManager.addView(floatingButton, params)
    }

    private fun handleButtonClick() {
        val reelUrl = getInstagramUrlFromClipboard() ?: run {
            Toast.makeText(this, "Copy an Instagram Reel link first", Toast.LENGTH_LONG).show()
            return
        }

        ioScope.launch {
            val videoFile = downloadVideo(reelUrl)
            if (videoFile == null) {
                Toast.makeText(this@OverlayService, "Video download failed", Toast.LENGTH_LONG).show()
                return@launch
            }

            val uri = FileProvider.getUriForFile(
                this@OverlayService,
                "${packageName}.provider",
                videoFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "#Shorts")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.youtube")
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@OverlayService, "YouTube app is not installed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getInstagramUrlFromClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.getItemAt(0) ?: return null
        val text = item.text?.toString() ?: return null
        return if (text.contains("instagram.com/reel/")) text else null
    }

    private fun downloadVideo(url: String): File? {
        return try {
            val client = OkHttpClient()
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute()
            if (!response.isSuccessful || response.body == null) return null

            val file = File(cacheDir, "instagram_short_${System.currentTimeMillis()}.mp4")
            val input = response.body!!.byteStream()
            val output = FileOutputStream(file)
            input.copyTo(output)
            input.close()
            output.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ReelToShorts")
            .setContentText("Floating button is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) {
            windowManager.removeViewImmediate(floatingButton)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
