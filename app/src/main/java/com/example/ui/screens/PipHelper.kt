package com.example.ui.screens

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import com.example.R

object PipHelper {

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(context: Context, player: Player?, width: Int = 0, height: Int = 0): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        
        if (width > 0 && height > 0) {
            val aspect = width.toFloat() / height.toFloat()
            val validAspect = aspect.coerceIn(10000f/23900f, 23900f/10000f)
            builder.setAspectRatio(android.util.Rational((validAspect * 10000).toInt(), 10000))
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        
        val actions = mutableListOf<RemoteAction>()
        
        if (player != null) {
            actions.add(
                createRemoteAction(
                    context,
                    R.drawable.ic_pip_prev,
                    "Previous",
                    PipActionReceiver.CONTROL_TYPE_PREV
                )
            )

            if (player.isPlaying) {
                actions.add(
                    createRemoteAction(
                        context,
                        R.drawable.ic_pip_pause,
                        "Pause",
                        PipActionReceiver.CONTROL_TYPE_PAUSE
                    )
                )
            } else {
                actions.add(
                    createRemoteAction(
                        context,
                        R.drawable.ic_pip_play,
                        "Play",
                        PipActionReceiver.CONTROL_TYPE_PLAY
                    )
                )
            }

            actions.add(
                createRemoteAction(
                    context,
                    R.drawable.ic_pip_next,
                    "Next",
                    PipActionReceiver.CONTROL_TYPE_NEXT
                )
            )
        }
        
        builder.setActions(actions)
        return builder.build()
    }

    private fun Context.findActivity(): android.app.Activity? = when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun updatePipParams(context: Context, player: Player?, width: Int = 0, height: Int = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val activity = context.findActivity() ?: return
                activity.setPictureInPictureParams(buildPipParams(context, player, width, height))
            } catch (e: Exception) {
                com.example.LogKeeper.logError("PipHelper", "Error setting PIP params: ${e.message}", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRemoteAction(
        context: Context,
        iconResId: Int,
        title: String,
        controlType: Int
    ): RemoteAction {
        val intent = Intent(PipActionReceiver.ACTION_PIP_CONTROL).apply {
            putExtra(PipActionReceiver.EXTRA_CONTROL_TYPE, controlType)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            controlType,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(context, iconResId)
        return RemoteAction(icon, title, title, pendingIntent)
    }
}
