package com.myfit.tracker.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Audio focus for Pip's voice and the meditation sounds: they stop when a call comes in or another app starts
 * playing, and other apps' music ducks (lowers) while Pip talks.
 */
class Focus(context: Context, private val transient: Boolean, private val onLoss: () -> Unit) {
    private val am = context.applicationContext.getSystemService(AudioManager::class.java)
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
    }
    private val req: AudioFocusRequest = AudioFocusRequest.Builder(if (transient) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(if (transient) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
            .setContentType(if (transient) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener(listener)
        .build()

    /** False when focus is refused (e.g. during a phone call) — don't play then. */
    fun request(): Boolean = runCatching { am?.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_FAILED }.getOrDefault(true)
    fun abandon() { runCatching { am?.abandonAudioFocusRequest(req) } }
}
