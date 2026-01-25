package com.tabula.v3

import android.app.Application
import coil.Coil
import com.tabula.v3.di.CoilSetup

/**
 * Tabula Application
 *
 * 初始化全局单例配置
 */
class TabulaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 设置全局 Coil ImageLoader
        Coil.setImageLoader(CoilSetup.getImageLoader(this))
    }
}
