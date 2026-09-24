package com.example.ime.voice

/**
 * IPC message tokens and parameter keys for communication between
 * the main keyboard process (:root) and the isolated voice service (:voice).
 */
object VoiceIpcProtocol {
    // Client -> Service Commands
    const val CMD_REGISTER_CLIENT = 1
    const val CMD_UNREGISTER_CLIENT = 2
    const val CMD_START_RECORDING = 3
    const val CMD_PAUSE_RECORDING = 4
    const val CMD_RESUME_RECORDING = 5
    const val CMD_STOP_RECORDING = 6
    const val CMD_SET_GAIN = 7
    const val CMD_SET_TEMPERATURE = 8
    const val CMD_FORCE_RELEASE = 9

    // Service -> Client Events
    const val EVENT_STATE_CHANGED = 101
    const val EVENT_RMS_UPDATE = 102
    const val EVENT_SPEECH_ACTIVITY = 103
    const val EVENT_ERROR = 104
    const val EVENT_TRANSCRIPTION_PREVIEW = 105
    const val EVENT_TRANSCRIPTION_COMMIT = 106

    // Bundle Data Keys
    const val KEY_RMS = "key_rms"
    const val KEY_ERROR_MESSAGE = "key_error_message"
    const val KEY_TRANSCRIPTION_TEXT = "key_transcription_text"
    const val KEY_COMMIT_TEXT = "key_commit_text"
    const val KEY_TEMPERATURE = "key_temperature"

    // Service States
    enum class ServiceState {
        IDLE,
        RECORDING,
        PAUSED,
        ERROR
    }
}
