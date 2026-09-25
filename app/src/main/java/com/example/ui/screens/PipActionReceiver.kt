package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player

class PipActionReceiver(private val player: Player?) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != ACTION_PIP_CONTROL) return
        when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
            CONTROL_TYPE_PLAY -> player?.play()
            CONTROL_TYPE_PAUSE -> player?.pause()
            CONTROL_TYPE_NEXT -> {
                if (player?.hasNextMediaItem() == true) {
                    player.seekToNextMediaItem()
                }
            }
            CONTROL_TYPE_PREV -> {
                if (player?.hasPreviousMediaItem() == true) {
                    player.seekToPreviousMediaItem()
                } else {
                    player?.seekTo(0)
                }
            }
        }
    }

    companion object {
        const val ACTION_PIP_CONTROL = "pip_control"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val CONTROL_TYPE_PLAY = 1
        const val CONTROL_TYPE_PAUSE = 2
        const val CONTROL_TYPE_NEXT = 3
        const val CONTROL_TYPE_PREV = 4
    }
}
