package com.example.reeltoshorts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.reeltoshorts.theme.ReelToShortsTheme

class MainActivity : ComponentActivity() {
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
                        onOpenYoutube = { openYouTube() }
                    )
                }
            }
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Allow display over other apps", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Overlay enabled", Toast.LENGTH_SHORT).show()
    }

    private fun openYouTube() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "YouTube app is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun ReelToShortsScreen(
    onStartOverlay: () -> Unit,
    onOpenYoutube: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Reel to Shorts",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Tap the floating button while Instagram is open, then send the file to YouTube Shorts.",
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge
        )

        Button(
            onClick = onStartOverlay,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text("Enable floating button")
        }

        Button(onClick = onOpenYoutube) {
            Text("Open YouTube")
        }
    }
}
