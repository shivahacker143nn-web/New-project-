package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class CustomVoiceRecorder(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var activeFile: File? = null
    private var startTimeMs: Long = 0L
    private var meterJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _waveformSamples = MutableStateFlow<List<Float>>(emptyList())
    val waveformSamples: StateFlow<List<Float>> = _waveformSamples.asStateFlow()

    private val _currentOutputFilePath = MutableStateFlow<String?>(null)
    val currentOutputFilePath: StateFlow<String?> = _currentOutputFilePath.asStateFlow()

    private val _playingFilePath = MutableStateFlow<String?>(null)
    val playingFilePath: StateFlow<String?> = _playingFilePath.asStateFlow()

    val voiceStorageDir: File
        get() = File(context.filesDir, VOICE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun createManagedSampleFile(displayName: String = "voice", extension: String = "m4a"): File {
        val safePrefix = sanitizeFilePrefix(displayName)
        val cleanExt = extension.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "m4a" }
        return File(voiceStorageDir, "${safePrefix}_${System.currentTimeMillis()}.$cleanExt")
    }

    fun sanitizeFilePrefix(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "voice" }
    }

    fun copyIntoManagedStorage(sourceFile: File, desiredName: String): Result<File> {
        return runCatching {
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "Source audio file does not exist or is empty."
            }
            val canonicalDir = voiceStorageDir.canonicalFile
            val canonicalSource = sourceFile.canonicalFile
            if (canonicalSource.parentFile == canonicalDir) {
                return@runCatching canonicalSource
            }
            val ext = sourceFile.extension.ifBlank { "m4a" }
            val dest = createManagedSampleFile(desiredName, ext)
            sourceFile.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied > MAX_SAMPLE_BYTES) {
                        dest.delete()
                        error("Audio file exceeds the 10 MB limit for custom voice profiles.")
                    }
                }
            }
            dest
        }
    }

    fun verifySampleExists(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        val f = File(filePath)
        return f.exists() && f.isFile && f.length() > 0L
    }

    fun listStoredSampleFiles(): List<File> =
        voiceStorageDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun getTotalStorageBytes(): Long =
        listStoredSampleFiles().sumOf { it.length() }

    fun startRecording(
        desiredName: String = "voice",
        onMaxDurationReached: () -> Unit = {}
    ): Result<File> {
        stopPlayback()
        stopRecording()

        if (!hasMicrophonePermission()) {
            return Result.failure(SecurityException("Microphone permission (RECORD_AUDIO) is required to record audio."))
        }

        return runCatching {
            val outputFile = createManagedSampleFile(desiredName, "m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setMaxDuration(MAX_RECORDING_DURATION_MS.toInt())
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            activeFile = outputFile
            _currentOutputFilePath.value = outputFile.absolutePath
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L
            _waveformSamples.value = emptyList()

            meterJob = scope.launch {
                val rollingSamples = ArrayDeque<Float>(40)
                while (isActive && _isRecording.value) {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    _recordingDurationMs.value = elapsed
                    val amp = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)
                        .coerceIn(0.04f, 1f)
                    _recordingAmplitude.value = amp
                    if (rollingSamples.size >= 36) {
                        rollingSamples.removeFirst()
                    }
                    rollingSamples.addLast(amp)
                    _waveformSamples.value = rollingSamples.toList()
                    if (elapsed >= MAX_RECORDING_DURATION_MS) {
                        onMaxDurationReached()
                        break
                    }
                    delay(80L)
                }
            }
            outputFile
        }
    }

    fun stopRecording(): Pair<File, Long>? {
        meterJob?.cancel()
        meterJob = null
        val file = activeFile
        val duration = System.currentTimeMillis() - startTimeMs
        _isRecording.value = false
        _recordingAmplitude.value = 0f

        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        activeFile = null

        return if (file != null && file.exists() && file.length() > 0) {
            _currentOutputFilePath.value = file.absolutePath
            file to duration.coerceAtLeast(500L)
        } else {
            file?.delete()
            _currentOutputFilePath.value = null
            null
        }
    }

    fun cancelRecording() {
        meterJob?.cancel()
        meterJob = null
        val file = activeFile
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        _recordingAmplitude.value = 0f
        _waveformSamples.value = emptyList()
        _currentOutputFilePath.value = null

        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        activeFile = null
        runCatching { file?.delete() }
    }

    fun importAudioFromUri(uri: Uri, desiredName: String = "imported"): Result<Pair<File, Long>> {
        return runCatching {
            val ext = resolveAudioExtension(uri)
            val outFile = createManagedSampleFile(desiredName, ext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied > MAX_SAMPLE_BYTES) {
                        outFile.delete()
                        error("Audio file exceeds the 10 MB limit for custom voice profiles.")
                    }
                }
            } ?: error("Unable to read selected audio file.")

            if (outFile.length() < 512L) {
                outFile.delete()
                error("Selected audio file is empty or too short.")
            }

            _currentOutputFilePath.value = outFile.absolutePath

            val duration = runCatching {
                val mp = MediaPlayer()
                mp.setDataSource(outFile.absolutePath)
                mp.prepare()
                val d = mp.duration.toLong()
                mp.release()
                d
            }.getOrDefault(0L)

            outFile to duration
        }
    }

    private fun resolveAudioExtension(uri: Uri): String {
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()

        val nameExt = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (nameExt in setOf("mp3", "wav", "m4a", "ogg", "aac", "flac", "opus")) {
            return nameExt
        }

        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty().lowercase()
        return when {
            mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
            mimeType.contains("wav") || mimeType.contains("x-wav") -> "wav"
            mimeType.contains("ogg") || mimeType.contains("opus") -> "ogg"
            mimeType.contains("aac") -> "aac"
            mimeType.contains("flac") -> "flac"
            else -> "m4a"
        }
    }

    fun togglePreview(filePath: String, onError: (String) -> Unit = {}) {
        if (_playingFilePath.value == filePath) {
            stopPlayback()
            return
        }
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) {
            onError("Audio file no longer exists on disk.")
            return
        }
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    _playingFilePath.value = null
                    runCatching { release() }
                    player = null
                }
                setOnErrorListener { _, _, _ ->
                    _playingFilePath.value = null
                    onError("Unable to play audio recording.")
                    true
                }
                prepare()
                start()
            }
            _playingFilePath.value = filePath
        }.onFailure {
            _playingFilePath.value = null
            onError("Playback failed: ${it.message}")
        }
    }

    fun stopPlayback() {
        _playingFilePath.value = null
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    fun deleteFile(filePath: String): Boolean {
        if (_playingFilePath.value == filePath) {
            stopPlayback()
        }
        if (_currentOutputFilePath.value == filePath) {
            _currentOutputFilePath.value = null
        }
        return runCatching { File(filePath).delete() }.getOrDefault(false)
    }

    companion object {
        const val VOICE_DIR_NAME = "custom_voices"
        const val MAX_RECORDING_DURATION_MS = 60_000L
        const val MAX_SAMPLE_BYTES = 10L * 1024L * 1024L
    }
}
