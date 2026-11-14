package shura.media.service

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CenterChannelAudioProcessor : AudioProcessor {
    @Volatile
    var enabled: Boolean = false

    private var pendingFormat = AudioFormat.NOT_SET
    private var buffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    fun releaseScratchBuffer() {
        if (!enabled) {
            buffer = AudioProcessor.EMPTY_BUFFER
            outputBuffer = AudioProcessor.EMPTY_BUFFER
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (!enabled || inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            pendingFormat = AudioFormat.NOT_SET
            buffer = AudioProcessor.EMPTY_BUFFER
            return AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = enabled && pendingFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val capacity = limit - position
        if (capacity <= 0) return

        if (buffer.capacity() < capacity) {
            buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        if (!enabled) {
            // Passthrough safeguard if toggled off mid-stream before reconfigure
            buffer.put(inputBuffer)
        } else {
            while (inputBuffer.position() < limit) {
                val left = inputBuffer.short.toInt()
                val right = inputBuffer.short.toInt()

                // Standard Mid/Side center extraction: Mid = (L+R)/2
                val mid = ((left + right) / 2).toShort()

                buffer.putShort(mid)
                buffer.putShort(mid)
            }
            inputBuffer.position(limit)
        }

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
        if (!enabled) {
            buffer = AudioProcessor.EMPTY_BUFFER
        }
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        pendingFormat = AudioFormat.NOT_SET
    }
}
