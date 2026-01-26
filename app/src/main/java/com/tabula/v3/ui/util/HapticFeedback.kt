package com.tabula.v3.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 触觉反馈工具类
 * 
 * 提供轻量级的震动反馈，提升交互体验
 */
object HapticFeedback {
    
    private var vibrator: Vibrator? = null
    private var enabled: Boolean = true
    private var strength: Int = 70

    /**
     * Update global haptic settings.
     */
    fun updateSettings(enabled: Boolean, strength: Int) {
        this.enabled = enabled
        this.strength = strength.coerceIn(0, 100)
    }
    
    /**
     * 初始化震动器
     */
    private fun getVibrator(context: Context): Vibrator? {
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
        return vibrator
    }

    private fun canVibrate(): Boolean {
        return enabled && strength > 0
    }

    private fun resolveAmplitude(scale: Float): Int {
        val normalized = (strength.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        val scaled = (normalized * scale).coerceIn(0f, 1f)
        return (scaled * 255f).toInt().coerceIn(1, 255)
    }

    private fun resolveDuration(base: Long, extraMax: Long): Long {
        val normalized = (strength.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        val extra = (extraMax * normalized).toLong()
        return (base + extra).coerceAtLeast(1L)
    }

    private fun vibrateOneShot(context: Context, baseDurationMs: Long, extraMaxMs: Long, scale: Float) {
        if (!canVibrate()) return
        try {
            val v = getVibrator(context) ?: return
            val durationMs = resolveDuration(baseDurationMs, extraMaxMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = resolveAmplitude(scale)
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 轻触反馈 - 用于普通点击
     */
    fun lightTap(context: Context) {
        vibrateOneShot(context, baseDurationMs = 22, extraMaxMs = 24, scale = 1.0f)
    }
    
    /**
     * 中等反馈 - 用于重要操作（如删除）
     */
    fun mediumTap(context: Context) {
        vibrateOneShot(context, baseDurationMs = 30, extraMaxMs = 30, scale = 1.2f)
    }
    
    /**
     * 重反馈 - 用于删除确认等
     */
    fun heavyTap(context: Context) {
        vibrateOneShot(context, baseDurationMs = 42, extraMaxMs = 38, scale = 1.4f)
    }
    
    /**
     * 双击反馈 - 用于成功操作
     */
    fun doubleTap(context: Context) {
        if (!canVibrate()) return
        try {
            val v = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = resolveAmplitude(1.2f)
                val timings = longArrayOf(0, 28, 90, 28)
                val amplitudes = intArrayOf(0, amplitude, 0, amplitude)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(25)
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
}
