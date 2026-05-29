package com.phial.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.sqrt

class TorchActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var root: View
    private lateinit var seekBar: SeekBar
    private lateinit var btnClose: TextView
    private lateinit var flashIcon: TextView
    private lateinit var shakeHint: TextView
    private lateinit var btnSettings: ImageView

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentBrightness = 100
    private var isFlashlightMode = false
    private var flashlightBrightness = 100  // 1–100

    // ── Camera ────────────────────────────────────────────────────────────────
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxTorchStrength = 1
    private var supportsStrength = false

    // ── Sensors ───────────────────────────────────────────────────────────────
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private val SHAKE_THRESHOLD = 14f
    private val SHAKE_COOLDOWN = 1000L

    // ── Timer ─────────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var autoOffRunnable: Runnable? = null

    // ── Shake listener ────────────────────────────────────────────────────────
    private val shakeListener = object : SensorEventListener {
        private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f
        private var initialized = false

        override fun onSensorChanged(event: SensorEvent) {
            if (!Prefs.shakeFlash(this@TorchActivity)) return
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            if (!initialized) { lastX = x; lastY = y; lastZ = z; initialized = true; return }
            val delta = sqrt((x-lastX)*(x-lastX) + (y-lastY)*(y-lastY) + (z-lastZ)*(z-lastZ))
            lastX = x; lastY = y; lastZ = z
            if (delta > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > SHAKE_COOLDOWN) {
                    lastShakeTime = now
                    toggleFlashlightMode()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_torch)

        root      = findViewById(R.id.root)
        seekBar   = findViewById(R.id.seekBar)
        btnClose  = findViewById(R.id.btnClose)
        flashIcon = findViewById(R.id.flashIcon)
        shakeHint = findViewById(R.id.shakeHint)
        btnSettings = findViewById(R.id.btnSettings)

        // Camera setup
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        setupCamera()

        // Sensor setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Initial brightness
        currentBrightness = if (Prefs.rememberBrightness(this)) Prefs.brightness(this) else 100
        seekBar.progress = currentBrightness

        if (Prefs.fadeIn(this)) {
            applyBrightness(5)
            fadeInBrightness(currentBrightness)
        } else {
            applyBrightness(currentBrightness)
        }

        applyColorMode()

        // Slider
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val clamped = progress.coerceAtLeast(5)
                if (clamped != progress) sb.progress = clamped
                if (isFlashlightMode) {
                    flashlightBrightness = clamped
                    setFlashStrength(clamped)
                } else {
                    currentBrightness = clamped
                    applyBrightness(clamped)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                if (!isFlashlightMode && Prefs.rememberBrightness(this@TorchActivity))
                    Prefs.setBrightness(this@TorchActivity, sb.progress.coerceAtLeast(5))
            }
        })

        btnClose.setOnClickListener { closeApp() }
        root.setOnClickListener { closeApp() }
        seekBar.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true); v.onTouchEvent(event); true
        }
        btnClose.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true); v.onTouchEvent(event); true
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnSettings.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true); v.onTouchEvent(event); true
        }

        scheduleAutoOff()
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private fun setupCamera() {
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cameraId?.let { id ->
                    val max = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    if (max > 1) { maxTorchStrength = max; supportsStrength = true }
                }
            }
        } catch (_: Exception) {}
    }

    private fun setFlashStrength(percent: Int) {
        val id = cameraId ?: return
        try {
            if (supportsStrength && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val level = (percent / 100f * maxTorchStrength).toInt().coerceAtLeast(1)
                cameraManager.turnOnTorchWithStrengthLevel(id, level)
            } else {
                cameraManager.setTorchMode(id, true)
            }
        } catch (_: Exception) {}
    }

    private fun turnOffFlash() {
        cameraId?.let { try { cameraManager.setTorchMode(it, false) } catch (_: Exception) {} }
    }

    // ── Flashlight mode toggle ────────────────────────────────────────────────

    private fun toggleFlashlightMode() {
        isFlashlightMode = !isFlashlightMode
        if (isFlashlightMode) {
            // Switch to flashlight: dim screen to natural, turn on LED
            applyBrightness(-1)  // -1 = system natural brightness
            root.setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
            flashIcon.visibility = View.VISIBLE
            shakeHint.text = "Shake to return to screen torch"
            shakeHint.setTextColor(android.graphics.Color.parseColor("#88FFFFFF"))
            seekBar.progress = flashlightBrightness
            setFlashStrength(flashlightBrightness)
            btnClose.setTextColor(android.graphics.Color.WHITE)
            btnSettings.setColorFilter(android.graphics.Color.argb(180, 255, 255, 255))
        } else {
            // Return to screen torch
            turnOffFlash()
            applyColorMode()
            flashIcon.visibility = View.GONE
            shakeHint.text = "Shake to switch to flashlight"
            shakeHint.setTextColor(android.graphics.Color.parseColor("#66000000"))
            seekBar.progress = currentBrightness
            applyBrightness(currentBrightness)
            btnClose.setTextColor(android.graphics.Color.parseColor("#777777"))
            btnSettings.setColorFilter(android.graphics.Color.argb(153, 0, 0, 0))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyBrightness(percent: Int) {
        val lp = window.attributes
        lp.screenBrightness = if (percent < 0) -1f else percent / 100f
        window.attributes = lp
    }

    private fun fadeInBrightness(target: Int) {
        ValueAnimator.ofInt(5, target).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyBrightness(it.animatedValue as Int) }
            start()
        }
    }

    private fun applyColorMode() {
        val mode = try {
            ColorMode.valueOf(Prefs.colorMode(this))
        } catch (_: Exception) { ColorMode.WHITE }
        val color = if (mode == ColorMode.CUSTOM) Prefs.customColor(this) else mode.color
        root.setBackgroundColor(color)
    }

    private fun scheduleAutoOff() {
        autoOffRunnable?.let { handler.removeCallbacks(it) }
        val minutes = Prefs.autoOffMinutes(this)
        if (minutes > 0) {
            autoOffRunnable = Runnable { closeApp() }
            handler.postDelayed(autoOffRunnable!!, minutes * 60 * 1000L)
        }
    }

    private fun closeApp() {
        turnOffFlash()
        autoOffRunnable?.let { handler.removeCallbacks(it) }
        finishAndRemoveTask()
    }

    // ── Volume buttons ────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!Prefs.volumeControl(this)) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                adjustBrightness(5); true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                adjustBrightness(-5); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun adjustBrightness(delta: Int) {
        if (isFlashlightMode) {
            flashlightBrightness = (flashlightBrightness + delta).coerceIn(5, 100)
            seekBar.progress = flashlightBrightness
            setFlashStrength(flashlightBrightness)
        } else {
            currentBrightness = (currentBrightness + delta).coerceIn(5, 100)
            seekBar.progress = currentBrightness
            applyBrightness(currentBrightness)
            if (Prefs.rememberBrightness(this)) Prefs.setBrightness(this, currentBrightness)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        applyColorMode()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(shakeListener)
        if (isFlashlightMode) turnOffFlash()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoOffRunnable?.let { handler.removeCallbacks(it) }
        turnOffFlash()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
    }
}
