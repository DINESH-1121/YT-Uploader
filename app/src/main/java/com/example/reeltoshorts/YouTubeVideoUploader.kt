package com.example.reeltoshorts

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

internal object YouTubeVideoUploader {
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos"
    private const val CHUNK_SIZE_BYTES = 8L * 1024L * 1024L
    private const val MAX_RETRIES = 5
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun upload(
        contentResolver: ContentResolver,
        videoUri: Uri,
        accessToken: String,
        title: String,
        description: String,
        privacyStatus: String,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val totalBytes = getVideoSize(contentResolver, videoUri)
        val mimeType = contentResolver.getType(videoUri)
            ?.takeIf { it.startsWith("video/") }
            ?: throw IOException("The selected item is not a recognized video file.")

        val sessionUrl = createUploadSession(
            accessToken = accessToken,
            totalBytes = totalBytes,
            mimeType = mimeType,
            title = title,
            description = description,
            privacyStatus = privacyStatus
        )

        uploadChunks(
            contentResolver = contentResolver,
            videoUri = videoUri,
            sessionUrl = sessionUrl,
            accessToken = accessToken,
            mimeType = mimeType,
            totalBytes = totalBytes,
            onProgress = onProgress
        )
    }

    private fun getVideoSize(contentResolver: ContentResolver, videoUri: Uri): Long {
        contentResolver.query(
            videoUri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0).takeIf { it > 0L }
                    ?: throw IOException("The selected video is empty.")
            }
        }
        throw IOException("Could not determine the video size. Save it to this device and select it again.")
    }

    private fun createUploadSession(
        accessToken: String,
        totalBytes: Long,
        mimeType: String,
        title: String,
        description: String,
        privacyStatus: String
    ): String {
        val metadata = JSONObject()
            .put(
                "snippet",
                JSONObject()
                    .put("title", title)
                    .put("description", description)
                    .put("categoryId", "22")
            )
            .put("status", JSONObject().put("privacyStatus", privacyStatus))

        val request = Request.Builder()
            .url("$UPLOAD_URL?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Length", totalBytes.toString())
            .header("X-Upload-Content-Type", mimeType)
            .post(metadata.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code != 200) throw apiException(response.code, response.body?.string())
            return response.header("Location")
                ?.takeIf { it.startsWith("https://www.googleapis.com/") }
                ?: throw IOException("YouTube did not return an upload session.")
        }
    }

    private suspend fun uploadChunks(
        contentResolver: ContentResolver,
        videoUri: Uri,
        sessionUrl: String,
        accessToken: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit
    ): String {
        var confirmedBytes = 0L
        var retryCount = 0

        while (confirmedBytes < totalBytes) {
            val chunkLength = min(CHUNK_SIZE_BYTES, totalBytes - confirmedBytes)
            val chunkStart = confirmedBytes
            val requestBody = VideoChunkRequestBody(
                contentResolver = contentResolver,
                videoUri = videoUri,
                mimeType = mimeType,
                startOffset = chunkStart,
                length = chunkLength
            ) { writtenBytes ->
                onProgress((chunkStart + writtenBytes).coerceAtMost(totalBytes), totalBytes)
            }
            val request = Request.Builder()
                .url(sessionUrl)
                .header("Authorization", "Bearer $accessToken")
                .header(
                    "Content-Range",
                    "bytes $chunkStart-${chunkStart + chunkLength - 1}/$totalBytes"
                )
                .put(requestBody)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 308 -> {
                            val serverOffset = parseUploadedBytes(response.header("Range"))
                                ?: 0L
                            if (serverOffset <= confirmedBytes) {
                                retryCount++
                                if (retryCount > MAX_RETRIES) {
                                    throw IOException("YouTube did not accept the video data. Check your connection and retry.")
                                }
                                delay(retryDelay(retryCount))
                            } else {
                                confirmedBytes = serverOffset.coerceAtMost(totalBytes)
                                retryCount = 0
                                onProgress(confirmedBytes, totalBytes)
                            }
                        }

                        response.code == 200 || response.code == 201 -> {
                            onProgress(totalBytes, totalBytes)
                            return parseVideoId(response.body?.string())
                        }

                        response.code in 500..599 -> {
                            retryCount++
                            if (retryCount > MAX_RETRIES) {
                                throw IOException("YouTube is temporarily unavailable. Please retry the upload.")
                            }
                            delay(retryDelay(retryCount))
                            val sessionState = querySession(
                                sessionUrl = sessionUrl,
                                accessToken = accessToken,
                                totalBytes = totalBytes
                            )
                            if (sessionState.videoId != null) {
                                onProgress(totalBytes, totalBytes)
                                return sessionState.videoId
                            }
                            confirmedBytes = sessionState.uploadedBytes
                            onProgress(confirmedBytes, totalBytes)
                        }

                        else -> throw apiException(response.code, response.body?.string())
                    }
                }
            } catch (exception: IOException) {
                if (exception is VideoSourceException) throw exception
                if (exception.message?.startsWith("YouTube") == true) throw exception
                retryCount++
                if (retryCount > MAX_RETRIES) {
                    throw IOException("Upload connection failed. Check your internet connection and retry.", exception)
                }
                delay(retryDelay(retryCount))
                val sessionState = querySession(
                    sessionUrl = sessionUrl,
                    accessToken = accessToken,
                    totalBytes = totalBytes
                )
                if (sessionState.videoId != null) {
                    onProgress(totalBytes, totalBytes)
                    return sessionState.videoId
                }
                confirmedBytes = sessionState.uploadedBytes
                onProgress(confirmedBytes, totalBytes)
            }
        }

        querySession(sessionUrl, accessToken, totalBytes).videoId?.let { return it }
        throw IOException("YouTube finished receiving the video without returning its video ID.")
    }

    private fun querySession(
        sessionUrl: String,
        accessToken: String,
        totalBytes: Long
    ): SessionState {
        val request = Request.Builder()
            .url(sessionUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Range", "bytes */$totalBytes")
            .put(ByteArray(0).toRequestBody(null))
            .build()

        httpClient.newCall(request).execute().use { response ->
            return when {
                response.code == 308 -> SessionState(
                    uploadedBytes = parseUploadedBytes(response.header("Range")) ?: 0L
                )
                response.code == 200 || response.code == 201 -> SessionState(
                    uploadedBytes = totalBytes,
                    videoId = parseVideoId(response.body?.string())
                )
                else -> throw apiException(response.code, response.body?.string())
            }
        }
    }

    private fun parseUploadedBytes(rangeHeader: String?): Long? {
        val lastByte = rangeHeader?.substringAfterLast('-')?.toLongOrNull() ?: return null
        return lastByte + 1L
    }

    private fun parseVideoId(responseBody: String?): String {
        val videoId = responseBody
            ?.let { JSONObject(it).optString("id") }
            ?.takeIf { it.isNotBlank() }
        return videoId ?: throw IOException("YouTube completed the request without returning a video ID.")
    }

    private fun apiException(statusCode: Int, responseBody: String?): IOException {
        val message = runCatching {
            JSONObject(responseBody.orEmpty())
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return IOException(message?.let { "YouTube API error ($statusCode): $it" }
            ?: "YouTube API error ($statusCode). Check the app's Cloud API setup and try again.")
    }

    private fun retryDelay(retryCount: Int): Long =
        (1_000L shl (retryCount - 1).coerceIn(0, 4))

    private data class SessionState(
        val uploadedBytes: Long,
        val videoId: String? = null
    )

    private class VideoChunkRequestBody(
        private val contentResolver: ContentResolver,
        private val videoUri: Uri,
        private val mimeType: String,
        private val startOffset: Long,
        private val length: Long,
        private val onBytesWritten: (Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = mimeType.toMediaType()

        override fun contentLength() = length

        override fun writeTo(sink: BufferedSink) {
            val input = try {
                contentResolver.openInputStream(videoUri)
            } catch (exception: SecurityException) {
                throw VideoSourceException("The app no longer has access to the selected video.", exception)
            } catch (exception: IOException) {
                throw VideoSourceException("Could not read the selected video from storage.", exception)
            } ?: throw VideoSourceException("The selected video is no longer available.")

            input.use {
                skipFully(it, startOffset)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRemaining = length
                var bytesWritten = 0L
                var lastReportedBytes = 0L
                while (bytesRemaining > 0L) {
                    val bytesRead = it.read(buffer, 0, min(buffer.size.toLong(), bytesRemaining).toInt())
                    if (bytesRead < 0) throw VideoSourceException("The selected video ended unexpectedly.")
                    sink.write(buffer, 0, bytesRead)
                    bytesRemaining -= bytesRead
                    bytesWritten += bytesRead
                    if (bytesWritten - lastReportedBytes >= 256L * 1024L || bytesRemaining == 0L) {
                        onBytesWritten(bytesWritten)
                        lastReportedBytes = bytesWritten
                    }
                }
            }
        }

        private fun skipFully(input: InputStream, byteCount: Long) {
            var remaining = byteCount
            while (remaining > 0L) {
                val skipped = input.skip(remaining)
                if (skipped > 0L) {
                    remaining -= skipped
                } else if (input.read() < 0) {
                    throw VideoSourceException("Could not read the selected video at the requested position.")
                } else {
                    remaining--
                }
            }
        }
    }

    private class VideoSourceException(message: String, cause: Throwable? = null) : IOException(message, cause)
}