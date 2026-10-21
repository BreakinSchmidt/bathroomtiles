package com.radialtiles.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isEnabled: Boolean = true

    private fun vibrateWithMaxStrength(effect: VibrationEffect) {
        val vib = vibrator ?: return
        if (!isEnabled || !vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                .build()
            vib.vibrate(effect, attrs)
        } else {
            vib.vibrate(effect)
        }
    }

    /**
     * Strong single vibration when a radial button is pressed (+50% intensity).
     */
    fun vibratePress() {
        vibrateWithMaxStrength(VibrationEffect.createOneShot(65, 255))
    }

    /**
     * Strong quick double vibration when the Home Assistant server confirms the action (+50% intensity).
     */
    fun vibrateServerConfirm() {
        val timings = longArrayOf(0, 55, 65, 70)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibrateWithMaxStrength(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /**
     * Rising double pulse for Turning ON (+50% intensity).
     */
    fun vibrateToggleOn() {
        val timings = longArrayOf(0, 55, 45, 95)
        val amplitudes = intArrayOf(0, 220, 0, 255)
        vibrateWithMaxStrength(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /**
     * Downward single pulse for Turning OFF (+50% intensity).
     */
    fun vibrateToggleOff() {
        vibrateWithMaxStrength(VibrationEffect.createOneShot(55, 180))
    }

    /**
     * Rhythmic pulse for activating Scenes or Automations (+50% intensity).
     */
    fun vibrateScene() {
        val timings = longArrayOf(0, 40, 30, 55, 30, 90)
        val amplitudes = intArrayOf(0, 200, 0, 240, 0, 255)
        vibrateWithMaxStrength(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /**
     * Micro tick for crown rotation and page switching.
     */
    fun vibrateCrownTick() {
        vibrateWithMaxStrength(VibrationEffect.createOneShot(25, 200))
    }

    /**
     * Double heavy buzz for error or network timeout (+50% intensity).
     */
    fun vibrateError() {
        val timings = longArrayOf(0, 165, 80, 180)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibrateWithMaxStrength(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
