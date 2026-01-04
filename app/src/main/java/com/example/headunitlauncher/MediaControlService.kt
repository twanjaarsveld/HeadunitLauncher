package com.example.headunitlauncher

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

class MediaControlService : NotificationListenerService() {

    companion object {
        fun sendCommand(context: Context, action: String) {
            val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, MediaControlService::class.java)
            val controllers = mm.getActiveSessions(component)

            // We send the command to the first active media session found
            controllers.firstOrNull()?.let { controller ->
                when (action) {
                    "PLAY_PAUSE" -> {
                        val state = controller.playbackState?.state
                        if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                            controller.transportControls.pause()
                        } else {
                            controller.transportControls.play()
                        }
                    }
                    "NEXT" -> controller.transportControls.skipToNext()
                    "PREV" -> controller.transportControls.skipToPrevious()
                }
            }
        }
    }
}