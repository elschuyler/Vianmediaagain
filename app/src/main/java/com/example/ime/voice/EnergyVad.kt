package com.example.ime.voice

import kotlin.math.sqrt

/**
 * Lightweight Energy-based Voice Activity Detection (VAD).
 * Analyzes 16kHz 16-bit mono PCM chunks to detect speech onset and offset
 * without requiring heavy external ML model inference in the real-time audio loop.
 */
class EnergyVad(
    private val speechThresholdMultiplier: Float = 2.5f,
    private val minSpeechFrames: Int = 3,
    private val minSilenceFrames: Int = 15
) {
    private var noiseFloor: Float = 100f
    private var consecutiveSpeechFrames: Int = 0
    private var consecutiveSilenceFrames: Int = 0

    var isSpeechDetected: Boolean = false
        private set

    /**
     * Process a single PCM frame of audio shorts.
     * Returns true if speech was detected in this frame.
     */
    fun processFrame(buffer: ShortArray, readSize: Int): Boolean {
        if (readSize <= 0) return isSpeechDetected

        var sumSquares = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        val frameEnergy = sqrt(sumSquares / readSize).toFloat()

        // Slowly adapt noise floor when quiet
        if (frameEnergy < noiseFloor * 1.5f) {
            noiseFloor = 0.95f * noiseFloor + 0.05f * frameEnergy
            if (noiseFloor < 30f) noiseFloor = 30f // Absolute floor threshold
        }

        val speechThreshold = noiseFloor * speechThresholdMultiplier

        if (frameEnergy > speechThreshold) {
            consecutiveSpeechFrames++
            consecutiveSilenceFrames = 0
            if (consecutiveSpeechFrames >= minSpeechFrames) {
                isSpeechDetected = true
            }
        } else {
            consecutiveSilenceFrames++
            consecutiveSpeechFrames = 0
            if (consecutiveSilenceFrames >= minSilenceFrames) {
                isSpeechDetected = false
            }
        }

        return isSpeechDetected
    }

    fun reset() {
        noiseFloor = 100f
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
        isSpeechDetected = false
    }
}
