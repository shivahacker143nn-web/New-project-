package com.example.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.example.data.model.VoiceGenderHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class VoiceAcousticProfile(
    val estimatedPitchHz: Float = 155f,
    val pitchMultiplier: Float = 1.0f,
    val rateMultiplier: Float = 1.0f,
    val warmth: Float = 0.5f,
    val expressiveness: Float = 0.65f,
    val clarityScore: Float = 0.78f,
    val detectedGenderHint: VoiceGenderHint = VoiceGenderHint.NEUTRAL,
    val sampleRateHz: Int = 44100,
    val analyzedDurationMs: Long = 0L
)

/**
 * Extracts real vocal characteristics (fundamental pitch Hz, spectral warmth, dynamic
 * expressiveness, clarity SNR, and cadence) from recorded or uploaded audio files.
 */
class VoiceCharacteristicsAnalyzer {

    suspend fun analyzeAudioFile(file: File): VoiceAcousticProfile = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext VoiceAcousticProfile()
        }

        val metadataDurationMs = runCatching {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            d
        }.getOrDefault(0L)

        val decoded = runCatching { decodePcmSampleWindow(file) }.getOrNull()
        if (decoded != null && decoded.first.isNotEmpty()) {
            val profile = analyzePcmSamples(decoded.first, decoded.second)
            return@withContext profile.copy(
                analyzedDurationMs = if (metadataDurationMs > 0L) metadataDurationMs else profile.analyzedDurationMs
            )
        }

        VoiceAcousticProfile(analyzedDurationMs = metadataDurationMs)
    }

    private fun decodePcmSampleWindow(file: File): Pair<ShortArray, Int>? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1

            val codec = MediaCodec.createDecoderByType(mime)
            val monoSamples = ArrayList<Short>(sampleRate * 3)
            val maxSamples = sampleRate * 3 // Analyze up to 3 seconds of active speech
            try {
                codec.configure(trackFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false
                var iterations = 0

                while (!sawOutputEos && monoSamples.size < maxSamples && iterations < 250) {
                    iterations++
                    if (!sawInputEos) {
                        val inIndex = codec.dequeueInputBuffer(10_000L)
                        if (inIndex >= 0) {
                            val inputBuf = codec.getInputBuffer(inIndex)
                            val sampleSize = if (inputBuf != null) extractor.readSampleData(inputBuf, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frame = ShortArray(shortBuf.remaining())
                            shortBuf.get(frame)
                            var idx = 0
                            while (idx < frame.size && monoSamples.size < maxSamples) {
                                var sum = 0
                                for (c in 0 until channels) {
                                    if (idx + c < frame.size) sum += frame[idx + c].toInt()
                                }
                                monoSamples.add((sum / channels).toShort())
                                idx += channels
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }

            if (monoSamples.isEmpty()) return null
            val arr = ShortArray(monoSamples.size) { monoSamples[it] }
            return arr to sampleRate
        } finally {
            runCatching { extractor.release() }
        }
    }

    companion object {
        /**
         * Pure DSP analyzer over mono 16-bit PCM samples. Estimates F0 pitch via normalized
         * autocorrelation on high-energy voiced windows, spectral warmth via zero-crossing rate,
         * dynamic expressiveness, and SNR clarity.
         */
        fun analyzePcmSamples(samples: ShortArray, sampleRate: Int): VoiceAcousticProfile {
            if (samples.size < 512 || sampleRate <= 0) {
                return VoiceAcousticProfile(sampleRateHz = sampleRate.coerceAtLeast(8000))
            }

            val frameSize = (sampleRate * 0.03).toInt().coerceIn(256, 2048)
            val hopSize = frameSize / 2
            val minLag = (sampleRate / 320f).toInt().coerceAtLeast(16)
            val maxLag = (sampleRate / 75f).toInt().coerceAtMost(frameSize - 1)

            val frameRmsList = mutableListOf<Float>()
            val detectedPitches = mutableListOf<Float>()
            val zcrValues = mutableListOf<Float>()

            var start = 0
            while (start + frameSize <= samples.size) {
                var energySum = 0.0
                var zeroCrossings = 0
                for (i in 0 until frameSize) {
                    val s = samples[start + i]
                    val norm = s / 32768.0
                    energySum += norm * norm
                    if (i > 0) {
                        val prev = samples[start + i - 1]
                        if ((s >= 0 && prev < 0) || (s < 0 && prev >= 0)) {
                            zeroCrossings++
                        }
                    }
                }
                val rms = sqrt(energySum / frameSize).toFloat()
                frameRmsList.add(rms)

                if (rms > 0.02f && maxLag > minLag) {
                    zcrValues.add(zeroCrossings.toFloat() / frameSize)
                    var bestLag = -1
                    var bestCorr = 0.35f
                    for (lag in minLag..maxLag) {
                        var num = 0.0
                        var denA = 0.0
                        var denB = 0.0
                        val limit = frameSize - lag
                        for (i in 0 until limit) {
                            val a = samples[start + i].toDouble()
                            val b = samples[start + i + lag].toDouble()
                            num += a * b
                            denA += a * a
                            denB += b * b
                        }
                        val denom = sqrt(denA * denB)
                        if (denom > 1e-6) {
                            val corr = (num / denom).toFloat()
                            if (corr > bestCorr) {
                                bestCorr = corr
                                bestLag = lag
                            }
                        }
                    }
                    if (bestLag > 0) {
                        val hz = sampleRate.toFloat() / bestLag.toFloat()
                        if (hz in 75f..320f) {
                            detectedPitches.add(hz)
                        }
                    }
                }
                start += hopSize
            }

            val medianPitchHz = if (detectedPitches.isNotEmpty()) {
                val sorted = detectedPitches.sorted()
                sorted[sorted.size / 2]
            } else {
                155f
            }

            val pitchMultiplier = (medianPitchHz / 155f).coerceIn(0.76f, 1.32f)
            val genderHint = when {
                medianPitchHz < 142f -> VoiceGenderHint.MALE
                medianPitchHz > 175f -> VoiceGenderHint.FEMALE
                else -> VoiceGenderHint.NEUTRAL
            }

            val avgZcr = if (zcrValues.isNotEmpty()) zcrValues.average().toFloat() else 0.12f
            val warmth = (1.0f - ((avgZcr - 0.03f) / 0.22f)).coerceIn(0.15f, 0.92f)

            val avgRms = if (frameRmsList.isNotEmpty()) frameRmsList.average().toFloat() else 0.05f
            val maxRms = frameRmsList.maxOrNull() ?: 0.05f
            val minRms = frameRmsList.filter { it > 0.001f }.minOrNull() ?: 0.005f

            val dynamicSpread = if (avgRms > 0.001f) ((maxRms - minRms) / avgRms).coerceIn(0.2f, 2.5f) else 0.8f
            val expressiveness = (dynamicSpread / 2.5f).coerceIn(0.32f, 0.90f)
            val clarity = (maxRms / (minRms + 0.02f) / 10f).coerceIn(0.35f, 0.96f)

            // Estimate cadence based on voiced energy transitions
            var activeTransitions = 0
            for (i in 1 until frameRmsList.size) {
                if (abs(frameRmsList[i] - frameRmsList[i - 1]) > avgRms * 0.45f) {
                    activeTransitions++
                }
            }
            val durationSec = (samples.size.toFloat() / sampleRate.toFloat()).coerceAtLeast(0.2f)
            val transitionRate = activeTransitions / durationSec
            val rateMultiplier = (0.90f + (transitionRate / 18f) * 0.24f).coerceIn(0.86f, 1.18f)

            return VoiceAcousticProfile(
                estimatedPitchHz = medianPitchHz,
                pitchMultiplier = pitchMultiplier,
                rateMultiplier = rateMultiplier,
                warmth = warmth,
                expressiveness = expressiveness,
                clarityScore = clarity,
                detectedGenderHint = genderHint,
                sampleRateHz = sampleRate,
                analyzedDurationMs = (durationSec * 1000f).toLong()
            )
        }
    }
}
