package com.example.service

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CenterChannelAudioProcessor : AudioProcessor {
    var enabled: Boolean = false

    private var pendingFormat = AudioFormat.NOT_SET
    private var buffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = enabled && pendingFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val frameCount = (limit - position) / 4 
        val capacity = frameCount * 4
        
        if (buffer.capacity() < capacity) {
            buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        
        while (inputBuffer.position() < limit) {
            val left = inputBuffer.short.toInt()
            val right = inputBuffer.short.toInt()
            
            // "Reverse Karaoke" trick: isolate the center by extracting common elements
            // Standard Mid/Side processing: Mid = (L+R)/2
            // Panning Mid to both L and R removes all stereo width, enhancing the center (vocals)
            val mid = ((left + right) / 2).toShort()
            
            buffer.putShort(mid)
            buffer.putShort(mid)
        }
        
        inputBuffer.position(limit)
        buffer.flip()
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        pendingFormat = AudioFormat.NOT_SET
    }
}
