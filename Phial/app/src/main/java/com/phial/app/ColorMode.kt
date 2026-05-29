package com.phial.app

import android.graphics.Color

enum class ColorMode(val label: String, val color: Int) {
    WHITE("White",  Color.parseColor("#FFFFFF")),
    WARM("Warm",    Color.parseColor("#FFF3C4")),
    AMBER("Amber",  Color.parseColor("#FFB347")),
    RED("Red",      Color.parseColor("#FF3B30")),
    CUSTOM("Custom", Color.parseColor("#FFFFFF")); // colour overridden by saved pref
}
