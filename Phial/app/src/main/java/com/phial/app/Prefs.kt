package com.phial.app

import android.content.Context

object Prefs {
    private const val NAME = "torch_prefs"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun rememberBrightness(ctx: Context) = prefs(ctx).getBoolean("remember_brightness", true)
    fun setRememberBrightness(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("remember_brightness", v).apply()

    fun brightness(ctx: Context) = prefs(ctx).getInt("brightness", 100)
    fun setBrightness(ctx: Context, v: Int) = prefs(ctx).edit().putInt("brightness", v).apply()

    fun fadeIn(ctx: Context) = prefs(ctx).getBoolean("fade_in", true)
    fun setFadeIn(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("fade_in", v).apply()

    fun shakeFlash(ctx: Context) = prefs(ctx).getBoolean("shake_flash", true)
    fun setShakeFlash(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("shake_flash", v).apply()

    fun volumeControl(ctx: Context) = prefs(ctx).getBoolean("volume_control", true)
    fun setVolumeControl(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("volume_control", v).apply()

    fun autoOffMinutes(ctx: Context) = prefs(ctx).getInt("auto_off_minutes", 0)
    fun setAutoOffMinutes(ctx: Context, v: Int) = prefs(ctx).edit().putInt("auto_off_minutes", v).apply()

    fun colorMode(ctx: Context) = prefs(ctx).getString("color_mode", ColorMode.WHITE.name) ?: ColorMode.WHITE.name
    fun setColorMode(ctx: Context, v: String) = prefs(ctx).edit().putString("color_mode", v).apply()

    fun darkMode(ctx: Context) = prefs(ctx).getBoolean("dark_mode", false)
    fun setDarkMode(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("dark_mode", v).apply()

    fun customColor(ctx: Context) = prefs(ctx).getInt("custom_color", android.graphics.Color.parseColor("#E8F4FF"))
    fun setCustomColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt("custom_color", v).apply()
}
