package com.example.headunitlauncher

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
        val scalePercent = prefs.getInt("ui_scale", 100)

        val config = Configuration(newBase.resources.configuration)
        // Adjusts the "Virtual" DPI of the app based on your slider percentage
        val originalDpi = newBase.resources.displayMetrics.densityDpi
        config.densityDpi = (originalDpi * (scalePercent / 100f)).toInt()

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
}