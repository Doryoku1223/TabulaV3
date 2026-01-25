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
    
    /**
     * 轻触反馈 - 用于普通点击
     */
    fun lightTap(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 中等反馈 - 用于重要操作（如删除）
     */
    fun mediumTap(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 重反馈 - 用于删除确认等
     */
    fun heavyTap(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
    
    /**
     * 双击反馈 - 用于成功操作
     */
    fun doubleTap(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 15, 50, 15)
                val amplitudes = intArrayOf(0, 100, 0, 100)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (e: Exception) {
            // 忽略震动错误
        }
    }
}
