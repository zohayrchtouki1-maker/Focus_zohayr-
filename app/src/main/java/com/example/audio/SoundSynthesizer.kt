package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.WhiteNoiseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundSynthesizer {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var noiseJob: Job? = null
    @Volatile private var currentVolume: Float = 0.6f
    @Volatile private var currentNoiseType: WhiteNoiseType = WhiteNoiseType.NONE

    fun playCheckSound() {
        scope.launch {
            // Crisp ascending two-note pop chime
            val durationSamples = (sampleRate * 0.22).toInt()
            val pcm = ShortArray(durationSamples)
            for (i in 0 until durationSamples) {
                val t = i.toDouble() / sampleRate
                val env1 = exp(-t * 28.0)
                val env2 = if (t > 0.05) exp(-(t - 0.05) * 22.0) else 0.0
                val s1 = sin(2.0 * PI * 783.99 * t) * env1 * 0.4 // G5
                val s2 = if (t > 0.05) sin(2.0 * PI * 1046.50 * t) * env2 * 0.45 else 0.0 // C6
                val sample = ((s1 + s2) * 16000.0).toInt().coerceIn(-32767, 32767)
                pcm[i] = sample.toShort()
            }
            playPcmOneShot(pcm)
        }
    }

    fun playCompletionChime() {
        scope.launch {
            // Warm harmonic bell triad chime (C5 -> E5 -> G5 -> C6)
            val totalSamples = (sampleRate * 1.4).toInt()
            val pcm = ShortArray(totalSamples)
            val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
            val starts = doubleArrayOf(0.0, 0.14, 0.28, 0.44)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sum = 0.0
                for (n in freqs.indices) {
                    val localT = t - starts[n]
                    if (localT >= 0.0) {
                        val env = exp(-localT * 4.5) * (1.0 - exp(-localT * 120.0))
                        val fundamental = sin(2.0 * PI * freqs[n] * localT)
                        val overtone = 0.25 * sin(2.0 * PI * freqs[n] * 2.0 * localT) * exp(-localT * 8.0)
                        sum += (fundamental + overtone) * env * 0.35
                    }
                }
                pcm[i] = (sum * 20000.0).toInt().coerceIn(-32767, 32767).toShort()
            }
            playPcmOneShot(pcm)
        }
    }

    private fun playPcmOneShot(pcm: ShortArray) {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size * 2)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((pcm.size * 1000L / sampleRate) + 120L)
        } catch (_: Exception) {
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
    }

    fun updateWhiteNoise(type: WhiteNoiseType, shouldPlay: Boolean, volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        if (!shouldPlay || type == WhiteNoiseType.NONE) {
            stopWhiteNoise()
            return
        }
        if (currentNoiseType == type && noiseJob?.isActive == true) {
            return
        }
        stopWhiteNoise()
        currentNoiseType = type
        startNoiseLoop(type)
    }

    fun stopWhiteNoise() {
        currentNoiseType = WhiteNoiseType.NONE
        noiseJob?.cancel()
        noiseJob = null
    }

    private fun startNoiseLoop(type: WhiteNoiseType) {
        noiseJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val bufSamples = sampleRate / 5 // 200ms chunk
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(bufSamples * 4)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                val pcm = ShortArray(bufSamples)
                val rng = Random(System.currentTimeMillis())

                // Filter states for procedural synthesis
                var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0
                var lp1 = 0.0; var lp2 = 0.0
                var sampleIndex = 0L
                var birdTrigger = 0L
                var birdEnv = 0.0

                while (isActive) {
                    val vol = currentVolume
                    for (i in 0 until bufSamples) {
                        val t = sampleIndex.toDouble() / sampleRate
                        val white = rng.nextDouble() * 2.0 - 1.0

                        // Paul Kellet pink noise approximation
                        b0 = 0.99765 * b0 + white * 0.0990460
                        b1 = 0.96300 * b1 + white * 0.2965164
                        b2 = 0.57000 * b2 + white * 1.0526913
                        val pink = (b0 + b1 + b2 + white * 0.1848) * 0.18

                        val rawSample = when (type) {
                            WhiteNoiseType.CLOCK -> {
                                // 1 Hz crisp mechanical tick/tock alternating pitch
                                val secPos = (sampleIndex % sampleRate).toDouble() / sampleRate
                                val isTock = (sampleIndex / sampleRate) % 2L == 1L
                                val tickFreq = if (isTock) 1650.0 else 2100.0
                                if (secPos < 0.035) {
                                    val env = exp(-secPos * 140.0)
                                    val click = sin(2.0 * PI * tickFreq * secPos) * 0.7 + white * 0.25
                                    click * env * 0.9
                                } else {
                                    white * 0.004
                                }
                            }
                            WhiteNoiseType.RAIN -> {
                                // Smooth filtered rain + soft random droplet plinks
                                lp1 = lp1 * 0.72 + pink * 0.28
                                val droplet = if (rng.nextDouble() < 0.0007) (rng.nextDouble() - 0.5) * 0.45 else 0.0
                                lp1 * 1.15 + droplet
                            }
                            WhiteNoiseType.WAVES -> {
                                // 0.12 Hz surf swell (~8.3s cycle) modulating filtered brown/pink surf
                                val swell = 0.25 + 0.75 * ((sin(2.0 * PI * 0.12 * t) + 1.0) * 0.5)
                                b3 = (b3 + white * 0.03) * 0.985
                                val surf = (b3 * 2.4 + pink * 0.45) * swell
                                surf * 0.85
                            }
                            WhiteNoiseType.FOREST -> {
                                // Gentle wind rustle + occasional soft distant bird harmonic
                                val breeze = 0.45 + 0.55 * ((sin(2.0 * PI * 0.08 * t) + 1.0) * 0.5)
                                lp2 = lp2 * 0.86 + pink * 0.14
                                if (sampleIndex >= birdTrigger) {
                                    birdTrigger = sampleIndex + sampleRate * (3L + rng.nextInt(5))
                                    birdEnv = 1.0
                                }
                                birdEnv *= 0.9993
                                val bird = if (birdEnv > 0.02) {
                                    val chirpFreq = 2600.0 + 450.0 * sin(2.0 * PI * 14.0 * t)
                                    sin(2.0 * PI * chirpFreq * t) * birdEnv * 0.06
                                } else 0.0
                                lp2 * breeze * 1.1 + bird
                            }
                            WhiteNoiseType.CAFE -> {
                                // Warm low-mid room murmur + gentle chatter warmth
                                b3 = (b3 + white * 0.035) * 0.98
                                lp1 = lp1 * 0.82 + pink * 0.18
                                val clink = if (rng.nextDouble() < 0.00018) {
                                    sin(2.0 * PI * 3200.0 * t) * 0.12
                                } else 0.0
                                (b3 * 1.6 + lp1 * 0.8) * 0.75 + clink
                            }
                            WhiteNoiseType.NONE -> 0.0
                        }

                        pcm[i] = (rawSample * vol * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
                        sampleIndex++
                    }
                    track.write(pcm, 0, pcm.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun release() {
        stopWhiteNoise()
        scope.cancel()
    }
}
