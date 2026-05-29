package com.phial.app

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*

class ColourPickerDialog(
    context: Context,
    private val initialColor: Int,
    private val onColorSelected: (Int) -> Unit
) : Dialog(context) {

    private lateinit var satBriView: SatBriView
    private lateinit var hueView: HueBarView
    private lateinit var previewView: View
    private lateinit var hexInput: EditText

    private var hue = 0f
    private var sat = 1f
    private var bri = 1f
    private var isUpdatingHex = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        layout.addView(TextView(context).apply {
            setText(R.string.picker_title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        })

        satBriView = SatBriView(context)
        layout.addView(satBriView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 500
        ).apply { bottomMargin = 32 })

        hueView = HueBarView(context)
        layout.addView(hueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 80
        ).apply { bottomMargin = 32 })

        val hexRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        hexInput = EditText(context).apply {
            hint = "#RRGGBB"
            setSingleLine()
            textSize = 14f
        }
        hexRow.addView(hexInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 24
        })

        previewView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setStroke(3, Color.GRAY)
            }
        }
        hexRow.addView(previewView, LinearLayout.LayoutParams(120, 120))
        layout.addView(hexRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val btnCancel = Button(context).apply { setText(R.string.picker_cancel) }
        val btnOk = Button(context).apply { setText(R.string.picker_ok) }
        btnRow.addView(btnCancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 16 })
        btnRow.addView(btnOk)
        layout.addView(btnRow)

        setContentView(layout)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        hue = hsv[0]; sat = hsv[1]; bri = hsv[2]

        satBriView.setHsv(hue, sat, bri)
        hueView.setHue(hue)
        updatePreview()
        hexInput.setText(colorToHex(currentColor()))

        satBriView.onChanged = { s, b ->
            sat = s; bri = b
            updatePreview()
            syncHex()
        }
        hueView.onChanged = { h ->
            hue = h
            satBriView.setHue(hue)
            updatePreview()
            syncHex()
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingHex) return
                val parsed = parseHex(s?.toString() ?: "") ?: return
                val hsv2 = FloatArray(3)
                Color.colorToHSV(parsed, hsv2)
                hue = hsv2[0]; sat = hsv2[1]; bri = hsv2[2]
                satBriView.setHsv(hue, sat, bri)
                hueView.setHue(hue)
                updatePreview()
            }
        })

        btnOk.setOnClickListener { onColorSelected(currentColor()); dismiss() }
        btnCancel.setOnClickListener { dismiss() }
    }

    private fun currentColor() = Color.HSVToColor(floatArrayOf(hue, sat, bri))

    private fun updatePreview() { previewView.setBackgroundColor(currentColor()) }

    private fun syncHex() {
        isUpdatingHex = true
        hexInput.setText(colorToHex(currentColor()))
        hexInput.setSelection(hexInput.text.length)
        isUpdatingHex = false
    }

    private fun colorToHex(color: Int) = "#%06X".format(color and 0xFFFFFF)

    private fun parseHex(input: String): Int? {
        val clean = input.trimStart('#')
        if (clean.length != 6 && clean.length != 8) return null
        return try { Color.parseColor("#$clean") } catch (_: Exception) { null }
    }
}

// ── Saturation / Brightness panel ──────────────────────────────────────────

class SatBriView(context: Context) : View(context) {

    var onChanged: ((Float, Float) -> Unit)? = null

    private var hue = 0f
    private var sat = 1f
    private var bri = 1f

    // Pre-allocated paint objects — never allocate in onDraw
    private val satPaint = Paint()
    private val briPaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
    }

    fun setHue(h: Float) { hue = h; invalidate() }
    fun setHsv(h: Float, s: Float, b: Float) { hue = h; sat = s; bri = b; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

        satPaint.shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, satPaint)

        briPaint.shader = LinearGradient(0f, 0f, 0f, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, briPaint)

        canvas.drawCircle(sat * w, (1f - bri) * h, 24f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        sat = (event.x / width).coerceIn(0f, 1f)
        bri = (1f - event.y / height).coerceIn(0f, 1f)
        onChanged?.invoke(sat, bri)
        invalidate()
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

// ── Hue bar ─────────────────────────────────────────────────────────────────

class HueBarView(context: Context) : View(context) {

    var onChanged: ((Float) -> Unit)? = null
    private var hue = 0f

    private val barPaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
    }
    private val hueColors = IntArray(13) { Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f)) }
    private val huePositions = FloatArray(13) { it / 12f }

    fun setHue(h: Float) { hue = h; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = h / 2f
        barPaint.shader = LinearGradient(0f, 0f, w, 0f, hueColors, huePositions, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, barPaint)
        canvas.drawCircle(hue / 360f * w, h / 2f, r - 4f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        hue = (event.x / width * 360f).coerceIn(0f, 360f)
        onChanged?.invoke(hue)
        invalidate()
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}