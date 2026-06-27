package com.moshbari.yaptext.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.moshbari.yaptext.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Recording + transcription — Android port of WhisperService.swift.
 *
 * Records mono 16 kHz AAC/m4a, then uploads it to the YapText server's
 * /transcribe endpoint (same backend as iOS). The server routes to Whisper
 * or Sarvam based on the selected [Language].
 *
 * Recording controls (mirrors iOS):
 *  - 5-minute hard cap per session (auto-stop)
 *  - 30-second silence auto-stop (skipped in manual-stop mode)
 *  - Manual stop mode still supported
 */
class TranscriptionService(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    data class State(
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcribedText: String = "",
        val statusMessage: String = "Tap the mic to start",
        val errorMessage: String? = null,
        val elapsedSeconds: Double = 0.0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Whether to auto-stop on silence. false = manual stop only. */
    var manualStopOnly: Boolean = false

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var tickJob: Job? = null

    private var lastSpeechMs = 0L
    private var startMs = 0L
    private var warnedAboutCap = false
    private val minRecordingMs = 1000L

    private fun update(block: State.() -> State) {
        _state.value = _state.value.block()
    }

    fun toggle() {
        if (_state.value.isRecording) stopRecording() else startRecording()
    }

    // MARK: - Start

    fun startRecording() {
        update {
            copy(
                transcribedText = "",
                errorMessage = null,
                elapsedSeconds = 0.0,
            )
        }
        warnedAboutCap = false

        val file = File(context.cacheDir, "yaptext_${System.nanoTime()}.m4a")
        audioFile = file

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }

        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(16000)
            rec.setAudioEncodingBitRate(32000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        } catch (e: Exception) {
            rec.runCatching { release() }
            recorder = null
            update {
                copy(
                    statusMessage = "Mic not available",
                    errorMessage = e.localizedMessage ?: "Could not start recording",
                )
            }
            return
        }

        recorder = rec
        startMs = SystemClock.elapsedRealtime()
        lastSpeechMs = startMs
        update {
            copy(
                isRecording = true,
                statusMessage = if (manualStopOnly) "Recording — tap mic to stop" else "Listening...",
            )
        }

        // Tick ~5x/second: update elapsed, enforce cap, detect silence.
        tickJob = scope.launch {
            while (_state.value.isRecording) {
                delay(200)
                tick()
            }
        }
    }

    private fun tick() {
        val rec = recorder ?: return
        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        val elapsed = elapsedMs / 1000.0
        update { copy(elapsedSeconds = elapsed) }

        // 5-minute hard cap
        val remaining = Config.MAX_RECORDING_SECONDS - elapsed
        if (remaining <= 0) {
            update { copy(statusMessage = "5-minute limit reached") }
            stopRecording()
            return
        }
        if (!warnedAboutCap && remaining <= Config.RECORDING_WARNING_SECONDS) {
            warnedAboutCap = true
            update { copy(statusMessage = "Stopping in ${remaining.toInt()}s — 5-min limit") }
        }

        // Silence detection (skipped in manual-stop mode)
        if (manualStopOnly) return
        if (elapsedMs < minRecordingMs) return

        val amplitude = runCatching { rec.maxAmplitude }.getOrDefault(0)
        if (amplitude > Config.SILENCE_AMPLITUDE_THRESHOLD) {
            lastSpeechMs = SystemClock.elapsedRealtime()
        } else if ((SystemClock.elapsedRealtime() - lastSpeechMs) >= Config.SILENCE_TIMEOUT_SECONDS * 1000) {
            update { copy(statusMessage = "Silence — stopping") }
            stopRecording()
        }
    }

    // MARK: - Stop

    fun stopRecording() {
        tickJob?.cancel()
        tickJob = null

        val rec = recorder
        val durationSec = _state.value.elapsedSeconds
        recorder = null

        if (rec == null) {
            update { copy(isRecording = false, statusMessage = "Tap the mic to start") }
            return
        }

        runCatching { rec.stop() }
        runCatching { rec.release() }
        update { copy(isRecording = false) }

        val file = audioFile
        if (file != null && durationSec > 0.5 && file.exists() && file.length() > 500) {
            update { copy(isTranscribing = true, statusMessage = "Transcribing...") }
            scope.launch { uploadToServer(file, durationSec) }
        } else {
            update { copy(statusMessage = "Recording too short — try again") }
            file?.delete()
        }
    }

    // MARK: - Upload

    private suspend fun uploadToServer(file: File, durationSec: Double) = withContext(Dispatchers.IO) {
        val language = AppStorage.language

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("language", language.raw)
            .addFormDataPart(
                "audio", "audio.m4a",
                file.asRequestBody("audio/m4a".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("${Config.API_BASE_URL}/transcribe")
            .addHeader("X-App-Secret", Config.APP_SECRET)
            .post(body)
            .build()

        try {
            Http.client.newCall(request).execute().use { response ->
                val payload = response.body?.string()
                when {
                    response.code == 200 -> {
                        val text = payload
                            ?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
                            ?.trim()
                        if (!text.isNullOrEmpty()) {
                            // Track trial usage (server has no per-user record yet)
                            AppStorage.addTrialSecondsUsed(durationSec)
                            SubscriptionManager.noteTrialChanged()
                            update {
                                copy(
                                    isTranscribing = false,
                                    transcribedText = text,
                                    statusMessage = "Done! Tap a tone to polish",
                                )
                            }
                        } else {
                            update { copy(isTranscribing = false, statusMessage = "No speech detected") }
                        }
                    }
                    response.code == 401 -> update {
                        copy(
                            isTranscribing = false,
                            statusMessage = "App auth failed",
                            errorMessage = "Update YapText to the latest version",
                        )
                    }
                    response.code == 429 -> update {
                        copy(isTranscribing = false, statusMessage = "Server busy — wait a moment")
                    }
                    else -> {
                        val serverMsg = payload
                            ?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }
                            ?.takeIf { it.isNotEmpty() }
                            ?: "API error (${response.code})"
                        update {
                            copy(
                                isTranscribing = false,
                                statusMessage = "Transcription failed",
                                errorMessage = serverMsg.take(120),
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            update {
                copy(
                    isTranscribing = false,
                    statusMessage = "Network error",
                    errorMessage = e.localizedMessage ?: "unknown",
                )
            }
        } finally {
            file.delete()
        }
    }

    fun clearTranscription() {
        update { copy(transcribedText = "", errorMessage = null, statusMessage = "Tap the mic to start") }
    }
}
