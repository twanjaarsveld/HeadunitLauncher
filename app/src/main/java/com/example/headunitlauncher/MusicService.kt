package com.example.headunitlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MusicService : NotificationListenerService(), MediaSessionManager.OnActiveSessionsChangedListener {
    private var sessionManager: MediaSessionManager? = null
    private val callbacks = mutableMapOf<String, MediaController.Callback>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager?.addOnActiveSessionsChangedListener(this, ComponentName(this, MusicService::class.java))
        scanSessions()
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        scanSessions()
    }

    private fun scanSessions() {
        val controllers = sessionManager?.getActiveSessions(ComponentName(this, MusicService::class.java)) ?: return
        for (controller in controllers) {
            if (!callbacks.containsKey(controller.packageName)) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        state?.let {
                            val intent = Intent("com.example.headunitlauncher.MUSIC_STATE_CHANGED")
                            intent.putExtra("isPlaying", it.state == PlaybackState.STATE_PLAYING)
                            intent.setPackage(packageName)
                            sendBroadcast(intent)
                        }
                    }
                }
                try {
                    controller.registerCallback(cb)
                    callbacks[controller.packageName] = cb
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager?.removeOnActiveSessionsChangedListener(this)
    }
}