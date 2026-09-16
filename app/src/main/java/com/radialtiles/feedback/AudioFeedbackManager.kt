package com.radialtiles.feedback

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

class AudioFeedbackManager {

    private var toneGen: ToneGenerator? = null
    var isEnabled: Boolean = true

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
        } catch (e: Exception) {
            Log.w("AudioFeedbackManager", "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Upbeat high-frequency click tone for Turning ON.
     */
    fun playClickOn() {
        if (!isEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
        } catch (e: Exception) {
            Log.w("AudioFeedbackManager", "Error playing click on", e)
        }
    }

    /**
     * Subtle, lower-frequency click tone for Turning OFF.
     */
    fun playClickOff() {
        if (!isEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 35)
        } catch (e: Exception) {
            Log.w("AudioFeedbackManager", "Error playing click off", e)
        }
    }

    /**
     * Harmonious multi-tone chime for Scene execution.
     */
    fun playSceneChime() {
        if (!isEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 85)
        } catch (e: Exception) {
            Log.w("AudioFeedbackManager", "Error playing scene chime", e)
        }
    }

    /**
     * Error tone for failures.
     */
    fun playErrorTone() {
        if (!isEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
        } catch (e: Exception) {
            Log.w("AudioFeedbackManager", "Error playing error tone", e)
        }
    }

    fun release() {
        toneGen?.release()
        toneGen = null
    }
}
