package com.example.reeltoshorts

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.example.reeltoshorts.theme.ReelToShortsTheme

class MainActivity : ComponentActivity() {
    private val selectedVideoUri = mutableStateOf<Uri?>(null)
    private val statusMessage = mutableStateOf<String?>(null)
    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                selectedVideoUri.value = null
                statusMessage.value = "No video selected."
            } else {
                selectedVideoUri.value = uri
                statusMessage.value = "Video ready. Continue to YouTube to finish the upload."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReelToShortsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReelToShortsScreen(
                        onStartOverlay = { startOverlay() },
                        onStopOverlay = { stopOverlay() },
                        onOpenYoutube = { openYouTube() },
                        onChooseVideo = { chooseVideo() },
                        onSendVideo = { sendVideoToYouTube() },
                        selectedVideo = selectedVideoUri.value,
                        statusMessage = statusMessage.value
                    )
                }
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
            Toast.makeText(this, "Allow display over other apps", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Overlay enabled", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        statusMessage.value = "Floating button stopped."
    }

    private fun openYouTube() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "YouTube app is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseVideo() {
        selectedVideoUri.value = null
        statusMessage.value = "Choose a video file to continue."
        videoPicker.launch(arrayOf("video/*"))
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent?.getBooleanExtra(EXTRA_PICK_VIDEO, false) == true) {
            chooseVideo()
            return
        }

        if (incomingIntent?.action != Intent.ACTION_SEND) return

        val videoUri = IntentCompat.getParcelableExtra(
            incomingIntent,
            Intent.EXTRA_STREAM,
            Uri::class.java
        ) ?: incomingIntent.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
        if (videoUri == null) {
            selectedVideoUri.value = null
            statusMessage.value = "No video file was shared. Choose a saved video instead."
            return
        }

        selectedVideoUri.value = videoUri
        statusMessage.value = "Video received. Continue to YouTube to finish the upload."
    }

    private fun sendVideoToYouTube() {
        val videoUri = selectedVideoUri.value ?: run {
            statusMessage.value = "Choose or share a video first."
            return
        }

        try {
            val mimeType = contentResolver.getType(videoUri)
                ?.takeIf { it.startsWith("video/") }
                ?: "video/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, videoUri)
                clipData = ClipData.newUri(contentResolver, "video", videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(YOUTUBE_PACKAGE)
            }
            startActivity(shareIntent)
            statusMessage.value = "YouTube opened. Finish and track the upload there."
        } catch (_: ActivityNotFoundException) {
            statusMessage.value = "YouTube could not accept this video. Open YouTube and upload it from your library."
        } catch (_: SecurityException) {
            statusMessage.value = "YouTube could not access this video. Choose it from your library in YouTube."
        }
    }

    companion object {
        const val EXTRA_PICK_VIDEO = "com.example.reeltoshorts.EXTRA_PICK_VIDEO"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}

@Composable
private fun ReelToShortsScreen(
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenYoutube: () -> Unit,
    onChooseVideo: () -> Unit,
    onSendVideo: () -> Unit,
    selectedVideo: Uri?,
    statusMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Reel to Shorts",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Choose a saved video or share a video file here. YouTube handles sign-in and upload.",
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge
        )

        Button(
            onClick = onStartOverlay,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text("Enable floating button")
        }

        Button(
            onClick = onStopOverlay,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text("Disable floating button")
        }

        Button(
            onClick = onChooseVideo,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text("Choose video file")
        }

        if (selectedVideo != null) {
            Text(
                text = "Video selected. Upload progress will appear in YouTube.",
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onSendVideo,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text("Continue to YouTube")
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(onClick = onOpenYoutube) {
            Text("Open YouTube")
        }
    }
}
