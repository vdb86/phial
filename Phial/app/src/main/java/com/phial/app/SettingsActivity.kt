package com.phial.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindSwitch(R.id.switchRemember,
            { Prefs.rememberBrightness(this) },
            { Prefs.setRememberBrightness(this, it) })

        bindSwitch(R.id.switchFadeIn,
            { Prefs.fadeIn(this) },
            { Prefs.setFadeIn(this, it) })

        bindSwitch(R.id.switchShake,
            { Prefs.shakeFlash(this) },
            { Prefs.setShakeFlash(this, it) })

        bindSwitch(R.id.switchVolume,
            { Prefs.volumeControl(this) },
            { Prefs.setVolumeControl(this, it) })

        bindSwitch(R.id.switchDarkMode,
            { Prefs.darkMode(this) },
            { v ->
                Prefs.setDarkMode(this, v)
                AppCompatDelegate.setDefaultNightMode(
                    if (v) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                recreate()
            })

        // Auto-off
        val autoOffOptions = listOf(0, 1, 2, 5, 10)
        val autoOffLabels  = listOf("Off", "1 min", "2 min", "5 min", "10 min")
        val btnAutoOff = findViewById<TextView>(R.id.btnAutoOff)
        fun updateAutoOff() {
            val idx = autoOffOptions.indexOf(Prefs.autoOffMinutes(this)).coerceAtLeast(0)
            btnAutoOff.text = autoOffLabels[idx]
        }
        updateAutoOff()
        btnAutoOff.setOnClickListener {
            val next = (autoOffOptions.indexOf(Prefs.autoOffMinutes(this)).coerceAtLeast(0) + 1) % autoOffOptions.size
            Prefs.setAutoOffMinutes(this, autoOffOptions[next])
            updateAutoOff()
        }

        // Colour mode buttons
        val colorButtons = mapOf(
            R.id.btnColorWhite  to ColorMode.WHITE,
            R.id.btnColorWarm   to ColorMode.WARM,
            R.id.btnColorAmber  to ColorMode.AMBER,
            R.id.btnColorRed    to ColorMode.RED,
            R.id.btnColorCustom to ColorMode.CUSTOM
        )

        colorButtons.forEach { (id, mode) ->
            val btn = findViewById<View>(id)
            btn.setOnClickListener {
                if (mode == ColorMode.CUSTOM) {
                    showColorPicker(colorButtons)
                } else {
                    Prefs.setColorMode(this, mode.name)
                    updateColorSelection(colorButtons)
                }
            }
        }
        updateColorSelection(colorButtons)
        updateCustomSwatch()

        // Preview
        findViewById<TextView>(R.id.btnPreviewColor).setOnClickListener {
            startActivity(Intent(this, TorchActivity::class.java))
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun showColorPicker(colorButtons: Map<Int, ColorMode>) {
        ColourPickerDialog(
            context = this,
            initialColor = Prefs.customColor(this),
            onColorSelected = { color ->
                Prefs.setCustomColor(this, color)
                Prefs.setColorMode(this, ColorMode.CUSTOM.name)
                updateCustomSwatch()
                updateColorSelection(colorButtons)
            }
        ).show()
    }

    private fun updateCustomSwatch() {
        val swatch = findViewById<View>(R.id.btnColorCustom)
        val color = Prefs.customColor(this)
        // Dynamically set the swatch background colour
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            this, R.drawable.color_swatch_custom
        )?.mutate()
        (drawable as? android.graphics.drawable.GradientDrawable)?.setColor(color)
        swatch.background = drawable
    }

    private fun bindSwitch(id: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = get()
        sw.setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun updateColorSelection(buttons: Map<Int, ColorMode>) {
        val current = Prefs.colorMode(this)
        buttons.forEach { (id, mode) ->
            val btn = findViewById<View>(id)
            btn.alpha  = if (mode.name == current) 1f else 0.4f
            btn.scaleX = if (mode.name == current) 1.15f else 1f
            btn.scaleY = if (mode.name == current) 1.15f else 1f
        }
    }

    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.darkMode(applicationContext)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
