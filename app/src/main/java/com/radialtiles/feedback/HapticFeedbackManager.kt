package com.radialtiles.feedback

import android.content.Context
import android.os.Build
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

    /**
     * Rising double pulse for Turning ON.
     * Starts medium and peaks sharp, giving a tactile sensation of powering up.
     */
    fun vibrateToggleOn() {
        if (!isEnabled || vibrator?.hasVibrator() != true) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )
        ) {
            val comp = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 20)
                .compose()
            vibrator.vibrate(comp)
        } else {
            val timings = longArrayOf(0, 35, 30, 65)
            val amplitudes = intArrayOf(0, 160, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    /**
     * Soft downward single tick for Turning OFF.
     * Short and subdued.
     */
    fun vibrateToggleOff() {
        if (!isEnabled || vibrator?.hasVibrator() != true) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            )
        ) {
            val comp = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f)
                .compose()
            vibrator.vibrate(comp)
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(35, 110))
        }
    }

    /**
     * Rhythmic pulse for activating Scenes or Automations.
     */
    fun vibrateScene() {
        if (!isEnabled || vibrator?.hasVibrator() != true) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )
        ) {
            val comp = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30)
                .compose()
            vibrator.vibrate(comp)
        } else {
            val timings = longArrayOf(0, 25, 20, 35, 20, 60)
            val amplitudes = intArrayOf(0, 140, 0, 200, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    /**
     * Micro tick for crown rotation and page switching.
     */
    fun vibrateCrownTick() {
        if (!isEnabled || vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    /**
     * Double heavy buzz for error or network timeout.
     */
    fun vibrateError() {
        if (!isEnabled || vibrator?.hasVibrator() != true) return
        val timings = longArrayOf(0, 110, 60, 120)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
