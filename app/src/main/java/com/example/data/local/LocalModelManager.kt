package com.example.data.local

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class OfflineModelCatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val minRamLabel: String,
    val downloadUrl: String,
    val fileName: String
)

data class ModelDownloadState(
    val activeModelId: String? = null,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val statusMessage: String = "No automatic model download runs without your consent.",
    val errorMessage: String? = null
)

/**
 * Manages optional user-consented offline language model downloads.
 * Never downloads large models automatically without explicit user action.
 */
class LocalModelManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow(ModelDownloadState())
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private var activeJob: Job? = null

    val catalog: List<OfflineModelCatalogItem> = listOf(
        OfflineModelCatalogItem(
            id = "qwen2_5_0_5b_q4",
            name = "Qwen 2.5 0.5B Instruct (GGUF Q4_K_M)",
            description = "Compact multilingual offline model (~490 MB) suitable for local Ollama / llama.cpp runtime bridges.",
            sizeLabel = "~491 MB",
            sizeBytes = 491L * 1024L * 1024L,
            minRamLabel = "2+ GB RAM",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ),
        OfflineModelCatalogItem(
            id = "llama_3_2_1b_q4",
            name = "Llama 3.2 1B Instruct (GGUF Q4_K_M)",
            description = "Balanced mobile-friendly 1B offline weights (~808 MB) for local OpenAI-compatible inference servers.",
            sizeLabel = "~808 MB",
            sizeBytes = 808L * 1024L * 1024L,
            minRamLabel = "4+ GB RAM",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        )
    )

    private fun modelsDir(): File {
        val dir = File(context.filesDir, "offline_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAvailableStorageBytes(): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    fun isModelDownloaded(item: OfflineModelCatalogItem): Boolean {
        val file = File(modelsDir(), item.fileName)
        return file.exists() && file.length() > 1024L * 1024L
    }

    fun getDownloadedFile(item: OfflineModelCatalogItem): File = File(modelsDir(), item.fileName)

    fun deleteModel(item: OfflineModelCatalogItem) {
        runCatching { File(modelsDir(), item.fileName).delete() }
        _downloadState.value = ModelDownloadState(
            statusMessage = "Removed ${item.name} from local storage."
        )
    }

    suspend fun downloadModelWithConsent(item: OfflineModelCatalogItem) = withContext(Dispatchers.IO) {
        val freeBytes = getAvailableStorageBytes()
        if (freeBytes < item.sizeBytes + 100L * 1024L * 1024L) {
            _downloadState.value = ModelDownloadState(
                errorMessage = "Insufficient free storage. Need at least ${item.sizeLabel} plus 100 MB headroom."
            )
            return@withContext
        }

        val outFile = File(modelsDir(), item.fileName)
        val tempFile = File(modelsDir(), "${item.fileName}.part")
        _downloadState.value = ModelDownloadState(
            activeModelId = item.id,
            progress = 0f,
            totalBytes = item.sizeBytes,
            statusMessage = "Connecting to download ${item.name}..."
        )

        try {
            val request = Request.Builder().url(item.downloadUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadState.value = ModelDownloadState(
                        errorMessage = "Download failed (HTTP ${response.code}). Verify internet connection."
                    )
                    return@withContext
                }
                val body = response.body ?: error("Empty response body")
                val total = if (body.contentLength() > 0) body.contentLength() else item.sizeBytes
                val buffer = ByteArray(16 * 1024)
                var copied = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            val pct = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            _downloadState.value = ModelDownloadState(
                                activeModelId = item.id,
                                progress = pct,
                                downloadedBytes = copied,
                                totalBytes = total,
                                statusMessage = "Downloading ${item.name}: ${(pct * 100).toInt()}%"
                            )
                        }
                    }
                }
                if (tempFile.exists()) {
                    tempFile.renameTo(outFile)
                }
                _downloadState.value = ModelDownloadState(
                    statusMessage = "${item.name} downloaded (${copied / (1024 * 1024)} MB)."
                )
            }
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            _downloadState.value = ModelDownloadState(
                errorMessage = "Download interrupted: ${e.message}"
            )
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeJob = null
        _downloadState.value = ModelDownloadState(statusMessage = "Download cancelled.")
    }
}
