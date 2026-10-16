package com.example.reeltoshorts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.reeltoshorts.theme.ReelToShortsTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    private val selectedVideoUri = mutableStateOf<Uri?>(null)
    private val statusMessage = mutableStateOf<String?>(null)
    private val uploadedVideoUrl = mutableStateOf<String?>(null)
    private val videoTitle = mutableStateOf("My YouTube Short")
    private val videoDescription = mutableStateOf("")
    private val privacyStatus = mutableStateOf("private")
    private val uploadProgress = mutableStateOf(0f)
    private val isUploading = mutableStateOf(false)
    private val acceptedPolicies = mutableStateOf(false)
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                selectedVideoUri.value = null
                statusMessage.value = "No video selected."
            } else {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                setSelectedVideo(uri)
            }
        }
    private val authorizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                isUploading.value = false
                statusMessage.value = "YouTube authorization was cancelled."
            } else {
                runCatching {
                    authorizationClient.getAuthorizationResultFromIntent(result.data!!)
                }.onSuccess(::handleAuthorizationResult)
                    .onFailure(::showAuthorizationError)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreFormState(savedInstanceState)
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
                        onUploadVideo = { authorizeAndUpload() },
                        onDisconnectYouTube = { disconnectYouTube() },
                        selectedVideo = selectedVideoUri.value,
                        videoTitle = videoTitle.value,
                        onTitleChange = { videoTitle.value = it },
                        videoDescription = videoDescription.value,
                        onDescriptionChange = { videoDescription.value = it },
                        privacyStatus = privacyStatus.value,
                        onPrivacyStatusChange = { privacyStatus.value = it },
                        acceptedPolicies = acceptedPolicies.value,
                        onAcceptedPoliciesChange = { accepted ->
                            acceptedPolicies.value = accepted
                            getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                                .edit { putBoolean(KEY_ACCEPTED_POLICIES, accepted) }
                        },
                        onOpenPrivacyPolicy = { openExternalUrl(PRIVACY_POLICY_URL) },
                        onOpenAppTerms = { openExternalUrl(APP_TERMS_URL) },
                        onOpenYouTubeTerms = { openExternalUrl(YOUTUBE_TERMS_URL) },
                        uploadedVideoUrl = uploadedVideoUrl.value,
                        onOpenUploadedVideo = {
                            uploadedVideoUrl.value?.let(::openExternalUrl)
                        },
                        uploadProgress = uploadProgress.value,
                        isUploading = isUploading.value,
                        statusMessage = statusMessage.value
                    )
                }
            }
        }
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_VIDEO_URI, selectedVideoUri.value?.toString())
        outState.putString(KEY_VIDEO_TITLE, videoTitle.value)
        outState.putString(KEY_VIDEO_DESCRIPTION, videoDescription.value)
        outState.putString(KEY_PRIVACY_STATUS, privacyStatus.value)
        super.onSaveInstanceState(outState)
    }

    private fun restoreFormState(savedInstanceState: Bundle?) {
        acceptedPolicies.value = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED_POLICIES, false)
        if (savedInstanceState == null) return

        selectedVideoUri.value = savedInstanceState.getString(KEY_VIDEO_URI)?.let(Uri::parse)
        videoTitle.value = savedInstanceState.getString(KEY_VIDEO_TITLE) ?: videoTitle.value
        videoDescription.value = savedInstanceState.getString(KEY_VIDEO_DESCRIPTION).orEmpty()
        privacyStatus.value = savedInstanceState.getString(KEY_PRIVACY_STATUS) ?: privacyStatus.value
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
        val launchIntent = packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "YouTube app is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseVideo() {
        selectedVideoUri.value = null
        uploadProgress.value = 0f
        uploadedVideoUrl.value = null
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

        setSelectedVideo(videoUri)
    }

    private fun setSelectedVideo(videoUri: Uri) {
        selectedVideoUri.value = videoUri
        uploadProgress.value = 0f
        uploadedVideoUrl.value = null
        videoTitle.value = queryVideoTitle(videoUri) ?: "My YouTube Short"
        videoDescription.value = ""
        statusMessage.value = "Video ready. Choose its visibility, then upload to YouTube."
    }

    private fun queryVideoTitle(videoUri: Uri): String? {
        return runCatching {
            contentResolver.query(
                videoUri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                        ?.substringBeforeLast('.', missingDelimiterValue = cursor.getString(0))
                        ?.take(100)
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun authorizeAndUpload() {
        if (selectedVideoUri.value == null) {
            statusMessage.value = "Choose or share a video first."
            return
        }
        val title = videoTitle.value.trim()
        if (title.isBlank()) {
            statusMessage.value = "Enter a title for the video."
            return
        }
        if (title.length > MAX_TITLE_CHARACTERS) {
            statusMessage.value = "YouTube titles must be 100 characters or fewer."
            return
        }
        if (title.contains('<') || title.contains('>')) {
            statusMessage.value = "YouTube titles cannot contain < or > characters."
            return
        }
        if (videoDescription.value.toByteArray(Charsets.UTF_8).size > MAX_DESCRIPTION_BYTES) {
            statusMessage.value = "YouTube descriptions must be 5,000 UTF-8 bytes or fewer."
            return
        }
        if (videoDescription.value.contains('<') || videoDescription.value.contains('>')) {
            statusMessage.value = "YouTube descriptions cannot contain < or > characters."
            return
        }
        if (!acceptedPolicies.value) {
            statusMessage.value = "Review and accept the Privacy Policy and YouTube Terms before uploading."
            return
        }

        isUploading.value = true
        uploadProgress.value = 0f
        statusMessage.value = "Requesting YouTube upload permission…"
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_UPLOAD_SCOPE)))
            .build()

        authorizationClient.authorize(request)
            .addOnSuccessListener(::handleAuthorizationResult)
            .addOnFailureListener(::showAuthorizationError)
    }

    private fun handleAuthorizationResult(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            if (pendingIntent == null) {
                showAuthorizationError(IllegalStateException("Google did not return an authorization prompt."))
                return
            }
            runCatching {
                authorizationLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            }.onFailure(::showAuthorizationError)
            return
        }

        val accessToken = result.accessToken
        if (accessToken.isNullOrBlank()) {
            showAuthorizationError(IllegalStateException("Google did not provide an upload access token."))
            return
        }
        uploadVideo(accessToken)
    }

    private fun uploadVideo(accessToken: String) {
        val videoUri = selectedVideoUri.value ?: run {
            isUploading.value = false
            statusMessage.value = "Choose or share a video first."
            return
        }
        val title = videoTitle.value
        val description = videoDescription.value
        val visibility = privacyStatus.value

        statusMessage.value = "Preparing upload…"
        lifecycleScope.launch {
            try {
                val videoId = YouTubeVideoUploader.upload(
                    contentResolver = contentResolver,
                    videoUri = videoUri,
                    accessToken = accessToken,
                    title = title,
                    description = description,
                    privacyStatus = visibility
                ) { uploadedBytes, totalBytes ->
                    val progress = uploadedBytes.toFloat() / totalBytes.toFloat()
                    runOnUiThread {
                        if (!isDestroyed && isUploading.value) {
                            uploadProgress.value = progress
                            if (progress < 1f) {
                                statusMessage.value = "Uploading ${(progress * 100).toInt()}%..."
                            }
                        }
                    }
                }
                uploadProgress.value = 1f
                uploadedVideoUrl.value = "https://youtu.be/$videoId"
                statusMessage.value = "YouTube accepted the upload. Video processing may continue."
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                statusMessage.value = exception.message ?: "YouTube upload failed. Please try again."
            } finally {
                isUploading.value = false
            }
        }
    }

    private fun showAuthorizationError(exception: Throwable) {
        isUploading.value = false
        statusMessage.value = "YouTube authorization failed. Check Cloud OAuth setup and try again. ${exception.message.orEmpty()}"
    }

    private fun disconnectYouTube() {
        val request = RevokeAccessRequest.builder()
            .setScopes(listOf(Scope(YOUTUBE_UPLOAD_SCOPE)))
            .build()
        authorizationClient.revokeAccess(request)
            .addOnSuccessListener { statusMessage.value = "YouTube access disconnected." }
            .addOnFailureListener { statusMessage.value = "Could not disconnect YouTube access. ${it.message.orEmpty()}" }
    }

    private fun openExternalUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { statusMessage.value = "Could not open link. ${it.message.orEmpty()}" }
    }

    companion object {
        const val EXTRA_PICK_VIDEO = "com.example.reeltoshorts.EXTRA_PICK_VIDEO"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.upload"
        private const val PREFERENCES_NAME = "reel_to_shorts_preferences"
        private const val KEY_ACCEPTED_POLICIES = "accepted_youtube_upload_policies"
        private const val KEY_VIDEO_URI = "selected_video_uri"
        private const val KEY_VIDEO_TITLE = "selected_video_title"
        private const val KEY_VIDEO_DESCRIPTION = "selected_video_description"
        private const val KEY_PRIVACY_STATUS = "selected_video_privacy_status"
        private const val MAX_TITLE_CHARACTERS = 100
        private const val MAX_DESCRIPTION_BYTES = 5_000
        private const val PRIVACY_POLICY_URL =
            "https://github.com/DINESH-1121/YT-Uploader/blob/main/PRIVACY_POLICY.md"
        private const val APP_TERMS_URL =
            "https://github.com/DINESH-1121/YT-Uploader/blob/main/TERMS_OF_SERVICE.md"
        private const val YOUTUBE_TERMS_URL = "https://www.youtube.com/t/terms"
    }
}

@Composable
private fun ReelToShortsScreen(
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenYoutube: () -> Unit,
    onChooseVideo: () -> Unit,
    onUploadVideo: () -> Unit,
    onDisconnectYouTube: () -> Unit,
    selectedVideo: Uri?,
    videoTitle: String,
    onTitleChange: (String) -> Unit,
    videoDescription: String,
    onDescriptionChange: (String) -> Unit,
    privacyStatus: String,
    onPrivacyStatusChange: (String) -> Unit,
    acceptedPolicies: Boolean,
    onAcceptedPoliciesChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAppTerms: () -> Unit,
    onOpenYouTubeTerms: () -> Unit,
    onOpenUploadedVideo: () -> Unit,
    uploadedVideoUrl: String?,
    uploadProgress: Float,
    isUploading: Boolean,
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
            text = "Choose or share a video file, then authorize YT Uploader to upload it to the channel linked to your selected Google account.",
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
            modifier = Modifier.padding(bottom = 12.dp),
            enabled = !isUploading
        ) {
            Text("Choose video file")
        }

        if (selectedVideo != null) {
            Text(
                text = "Video selected. The upload will be sent directly to YouTube.",
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = videoTitle,
                onValueChange = onTitleChange,
                label = { Text("Video title") },
                supportingText = { Text("Up to 100 characters") },
                singleLine = true,
                enabled = !isUploading,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = videoDescription,
                onValueChange = onDescriptionChange,
                label = { Text("Video description") },
                supportingText = { Text("Up to 5,000 UTF-8 bytes") },
                minLines = 3,
                maxLines = 6,
                enabled = !isUploading,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            PrivacySelector(
                selected = privacyStatus,
                onSelected = onPrivacyStatusChange,
                enabled = !isUploading
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = acceptedPolicies,
                    onCheckedChange = onAcceptedPoliciesChange,
                    enabled = !isUploading
                )
                Text("I agree to the Privacy Policy and YT Uploader Terms; YouTube Terms also apply.")
            }

            Button(
                onClick = onUploadVideo,
                modifier = Modifier.padding(vertical = 12.dp),
                enabled = !isUploading && acceptedPolicies
            ) {
                Text(if (isUploading) "Uploading..." else "Authorize & upload")
            }

            if (isUploading) {
                LinearProgressIndicator(
                    progress = { uploadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (uploadedVideoUrl != null) {
            TextButton(onClick = onOpenUploadedVideo) {
                Text("Open uploaded video")
            }
        }

        Button(onClick = onOpenYoutube) {
            Text("Open YouTube")
        }

        OutlinedButton(
            onClick = onDisconnectYouTube,
            enabled = !isUploading,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Disconnect YouTube access")
        }

        Row {
            TextButton(onClick = onOpenPrivacyPolicy) {
                Text("Privacy policy")
            }
            TextButton(onClick = onOpenAppTerms) {
                Text("App terms")
            }
            TextButton(onClick = onOpenYouTubeTerms) {
                Text("YouTube Terms")
            }
        }
    }
}

@Composable
private fun PrivacySelector(
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean
) {
    val options = listOf("private", "unlisted", "public")
    var expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded.value = true }, enabled = enabled) {
            Text("Visibility: ${selected.replaceFirstChar(Char::uppercase)}")
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar(Char::uppercase)) },
                    onClick = {
                        onSelected(option)
                        expanded.value = false
                    }
                )
            }
        }
    }
}
