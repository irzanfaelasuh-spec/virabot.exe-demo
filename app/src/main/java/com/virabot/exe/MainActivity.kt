package com.virabot.exe

import android.app.Activity
import android.app.AlertDialog
import android.graphics.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var scene: SpiderSceneView
    private lateinit var status: TextView
    private var soundEnabled = true
    private var particlesEnabled = true
    private val prefs by lazy { getSharedPreferences("virabot-settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 6, 10)
        window.navigationBarColor = Color.rgb(5, 6, 10)
        soundEnabled = prefs.getBoolean("sound", true)
        particlesEnabled = prefs.getBoolean("particles", true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            setBackgroundColor(Color.rgb(5, 6, 10))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(label("VIRABOT.EXE", 23f, Color.WHITE, true))
        titleBox.addView(label("RED SPIDER SIMULATOR  /  BUILD 1.0", 9f, 0xFF87909E.toInt(), true))
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("●  OFFLINE", 10f, 0xFFFF434A.toInt(), true))
        root.addView(header)

        status = label("STATUS  //  IDLE", 11f, 0xFFFF555B.toInt(), true).apply {
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(0xFF11151D.toInt(), 10)
        }
        val statusLp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        root.addView(status, statusLp)

        scene = SpiderSceneView(this, soundEnabled, particlesEnabled) { safe ->
            runOnUiThread { status.text = if (safe) "STATUS  //  SAFE MODE — HUNT DISABLED" else "STATUS  //  IDLE — SIMULATION READY" }
        }
        val sceneLp = LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        }
        root.addView(scene, sceneLp)

        val hint = label("TAP SPIDER ONCE: WARNING SEQUENCE     •     TAP 4×: SAFE MODE", 9f, 0xFF747E8D.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(10))
        }
        root.addView(hint)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(actionButton("START DEMO") { scene.startDemo() }, weightedButton())
        row1.addView(actionButton("SETTINGS") { showSettings() }, weightedButton().apply { leftMargin = dp(8) })
        root.addView(row1)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row2.addView(actionButton("TOGGLE SAFE MODE") { scene.toggleSafeMode() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { topMargin = dp(8) })
        row2.addView(actionButton("RESET SCENE") { scene.resetScene() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { topMargin = dp(8); leftMargin = dp(8) })
        root.addView(row2)

        root.addView(label("SIMULATION ONLY  •  DOES NOT TOUCH OTHER APPS OR FILES", 8f, 0xFF586170.toInt(), true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
        })
        setContentView(root)
    }

    private fun showSettings() {
        val items = arrayOf("Retro sound effects", "Red binary particles")
        val checked = booleanArrayOf(soundEnabled, particlesEnabled)
        AlertDialog.Builder(this)
            .setTitle("virabot.exe // Settings")
            .setMultiChoiceItems(items, checked) { _, which, value ->
                if (which == 0) soundEnabled = value else particlesEnabled = value
                prefs.edit().putBoolean(if (which == 0) "sound" else "particles", value).apply()
                scene.updateSettings(soundEnabled, particlesEnabled)
            }
            .setPositiveButton("APPLY", null)
            .setNeutralButton("ABOUT") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("About virabot.exe")
                    .setMessage("A retro-styled spider animation simulator. All popups, bites, particles and sounds are visual effects inside this app. It does not request sensitive permissions, inspect other apps, or run secretly in the background.")
                    .setPositiveButton("OK", null).show()
            }
            .show()
    }

    private fun actionButton(text: String, click: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        typeface = Typeface.create("monospace", Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(0xFF171D27.toInt(), 10, 0xFF3B2028.toInt())
        setOnClickListener { click() }
    }

    private fun weightedButton() = LinearLayout.LayoutParams(0, dp(46), 1f)
    private fun label(text: String, size: Float, color: Int, mono: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (mono) typeface = Typeface.create("monospace", Typeface.BOLD)
    }
    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (::scene.isInitialized) scene.release()
        super.onDestroy()
    }
}

private class SpiderSceneView(
    private val activity: Activity,
    soundInitially: Boolean,
    particlesInitially: Boolean,
    private val onSafeChanged: (Boolean) -> Unit
) : View(activity) {
    private enum class Phase { IDLE, WARNING, SPLIT }
    private var phase = Phase.IDLE
    private var safeMode = false
    private var warningsLeft = 0
    private var phaseStart = SystemClock.uptimeMillis()
    private var tapCount = 0
    private var soundEnabled = soundInitially
    private var particlesEnabled = particlesInitially
    private val handler = Handler(Looper.getMainLooper())
    private var tapTask: Runnable? = null
    private val startTime = SystemClock.uptimeMillis()
    private val soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val warningSound = soundPool.load(activity, R.raw.retro_warning, 1)
    private val biteSound = soundPool.load(activity, R.raw.retro_bite, 1)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val red = Color.rgb(255, 40, 55)
    private val particles = ArrayList<Particle>()
    private val random = Random(1407)
    private var widthF = 1f
    private var heightF = 1f

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        contentDescription = "Animated red spider. Tap once for the warning sequence; tap four times quickly to activate safe mode."
    }

    fun updateSettings(sound: Boolean, particles: Boolean) {
        soundEnabled = sound; particlesEnabled = particles
    }

    fun toggleSafeMode() {
        safeMode = !safeMode
        if (safeMode) { phase = Phase.IDLE; warningsLeft = 0; particles.clear() }
        onSafeChanged(safeMode)
        invalidate()
    }

    fun startDemo() {
        if (safeMode) return
        warningsLeft = 5
        phase = Phase.WARNING
        phaseStart = SystemClock.uptimeMillis()
        play(warningSound)
        invalidate()
    }

    fun resetScene() {
        phase = Phase.IDLE; warningsLeft = 0; particles.clear(); tapCount = 0
        invalidate()
    }

    fun release() { soundPool.release(); tapTask?.let { handler.removeCallbacks(it) } }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        widthF = w.toFloat(); heightF = h.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val t = (now - startTime) / 1000f
        drawBackdrop(canvas, t)
        updatePhase(now)
        if (particlesEnabled) drawAmbientDust(canvas, t)
        if (phase == Phase.WARNING) drawWarning(canvas, t)
        if (phase == Phase.SPLIT) drawSplit(canvas, t)
        drawSpider(canvas, t)
        postInvalidateOnAnimation()
    }

    private fun drawBackdrop(c: Canvas, t: Float) {
        c.drawColor(Color.rgb(7, 8, 13))
        p.shader = LinearGradient(0f, 0f, widthF, heightF, intArrayOf(0xFF11131D.toInt(), 0xFF090B11.toInt(), 0xFF050609.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, widthF, heightF, p); p.shader = null
        p.color = 0x22FF2636; p.strokeWidth = 0.7f
        val step = 34f * resources.displayMetrics.density
        var x = 0f
        while (x <= widthF) { c.drawLine(x, 0f, x, heightF, p); x += step }
        var y = 0f
        while (y <= heightF) { c.drawLine(0f, y, widthF, y, p); y += step }
        val scanY = (t * 40f % (heightF + 80f)) - 40f
        p.shader = LinearGradient(0f, scanY - 32f, 0f, scanY + 32f, 0x00FF2636, 0x20FF2636, Shader.TileMode.CLAMP)
        c.drawRect(0f, scanY - 32f, widthF, scanY + 32f, p); p.shader = null
        p.color = 0x18FF2335
        c.drawCircle(widthF * .5f, heightF * .48f, min(widthF, heightF) * .34f, p)
    }

    private fun drawAmbientDust(c: Canvas, t: Float) {
        for (i in 0 until 18) {
            val x = ((i * 79f + sin(t * .22f + i) * 28f) % widthF).coerceAtLeast(0f)
            val y = ((i * 113f - t * (7f + i % 4)) % heightF + heightF) % heightF
            p.color = if (i % 3 == 0) 0x66FF3945 else 0x2DFF2638
            c.drawCircle(x, y, if (i % 4 == 0) 2.2f else 1.2f, p)
        }
    }

    private fun drawSpider(c: Canvas, t: Float) {
        val scale = resources.displayMetrics.density
        val cx = widthF * (.5f + .20f * sin(t * .24f))
        val cy = heightF * (.48f + .045f * sin(t * .83f))
        val bob = sin(t * 3.0f) * 2.4f * scale
        val s = min(widthF, heightF) * .00105f * scale.coerceAtLeast(1f)
        val r = (35f * s).coerceAtLeast(16f)
        c.save(); c.translate(cx, cy + bob); c.rotate(sin(t * .55f) * 3.5f)

        // Soft red aura and a thin web-like halo.
        p.color = 0x14FF172A; c.drawCircle(0f, 0f, r * 3.2f, p)
        p.color = 0x44FF2638; p.style = Paint.Style.STROKE; p.strokeWidth = 1.1f * scale
        c.drawCircle(0f, 0f, r * 2.55f, p); p.style = Paint.Style.FILL
        p.color = 0x28FF3344
        for (a in 0..7) {
            val ang = a * PI / 4.0
            c.drawLine((cos(ang) * r * 1.8).toFloat(), (sin(ang) * r * 1.8).toFloat(), (cos(ang) * r * 2.45).toFloat(), (sin(ang) * r * 2.45).toFloat(), p)
        }

        // Eight articulated legs with independent, smooth gait curves.
        for (side in -1..1 step 2) {
            for (i in 0..3) {
                val phaseLeg = t * 4.0f + i * .76f + if (side < 0) PI.toFloat() else 0f
                val sway = sin(phaseLeg) * 5f * scale
                val anchorY = (i - 1.5f) * r * .38f
                val kneeX = side * (r * (1.10f + i * .07f))
                val kneeY = anchorY + (i - 1.5f) * r * .28f + sway * .45f
                val footX = side * (r * (2.05f + (i % 2) * .18f))
                val footY = anchorY + (if (i < 2) -1 else 1) * r * (1.10f + .09f * i) + sway
                p.color = 0xFF8E1523.toInt(); p.strokeWidth = 5.5f * scale
                val path = Path().apply { moveTo(side * r * .55f, anchorY); quadTo(kneeX * .55f, anchorY - r * .12f, kneeX, kneeY); quadTo(footX * .84f, footY - sway * .3f, footX, footY) }
                c.drawPath(path, p)
                p.color = red; p.strokeWidth = 2.3f * scale; c.drawPath(path, p)
                p.color = 0xFFFF6970.toInt(); c.drawCircle(footX, footY, 2f * scale, p)
            }
        }

        // Abdomen and cephalothorax.
        p.color = 0xFF5A0712.toInt(); c.drawOval(-r * .68f, -r * .08f, r * .68f, r * 1.40f, p)
        p.color = 0xFFE51A2D.toInt(); c.drawOval(-r * .55f, -r * .03f, r * .55f, r * 1.28f, p)
        p.color = 0xFF9B0C1B.toInt(); c.drawOval(-r * .38f, r * .08f, r * .20f, r * 1.05f, p)
        p.color = 0xFF6D0715.toInt(); c.drawOval(-r * .56f, -r * .82f, r * .56f, r * .38f, p)
        p.color = 0xFFFF2639.toInt(); c.drawOval(-r * .47f, -r * .80f, r * .47f, r * .31f, p)
        p.color = 0xFFFF8A91.toInt(); c.drawCircle(-r * .22f, -r * .62f, r * .09f, p)
        p.color = 0xFFFFD7D9.toInt(); c.drawCircle(-r * .22f, -r * .62f, r * .035f, p)
        // Mandibles.
        p.color = 0xFFFF4050.toInt(); p.strokeWidth = 3.5f * scale
        c.drawLine(-r * .18f, r * .24f, -r * .36f, r * .56f, p)
        c.drawLine(r * .18f, r * .24f, r * .36f, r * .56f, p)
        c.restore()
    }

    private fun popupRect(): RectF {
        val w = min(widthF * .84f, 350f * resources.displayMetrics.density)
        val h = 142f * resources.displayMetrics.density
        return RectF((widthF - w) / 2f, heightF * .12f, (widthF + w) / 2f, heightF * .12f + h)
    }

    private fun drawWarning(c: Canvas, t: Float) {
        val rect = popupRect()
        val blink = 0.90f + .10f * sin(t * 3.0f)
        c.save(); c.scale(blink, blink, rect.centerX(), rect.centerY())
        drawRetroPanel(c, rect, "virabot.exe - Warning", "What are you doing!")
        val btn = okRect(rect)
        p.color = 0xFFE1E1E1.toInt(); c.drawRoundRect(btn, 2f, 2f, p)
        p.color = 0xFF555555.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1f; c.drawRoundRect(btn, 2f, 2f, p); p.style = Paint.Style.FILL
        drawText(c, "OK", btn.centerX(), btn.centerY() + 4f, 11f, Color.BLACK, true, Paint.Align.CENTER)
        c.restore()
        drawText(c, "WARNING SEQUENCE  •  $warningsLeft / 5", widthF / 2f, rect.bottom + 24f, 9f, 0xFFFF6D75.toInt(), true, Paint.Align.CENTER)
    }

    private fun drawRetroPanel(c: Canvas, rect: RectF, title: String, message: String) {
        p.color = 0x88000000.toInt(); c.drawRoundRect(RectF(rect.left + 5, rect.top + 7, rect.right + 5, rect.bottom + 7), 5f, 5f, p)
        p.color = 0xFFC0C0C0.toInt(); c.drawRect(rect, p)
        p.color = 0xFF707070.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f; c.drawRect(rect, p); p.style = Paint.Style.FILL
        val titleRect = RectF(rect.left + 3, rect.top + 3, rect.right - 3, rect.top + 28f)
        p.shader = LinearGradient(titleRect.left, titleRect.top, titleRect.right, titleRect.top, 0xFF182D78.toInt(), 0xFF3976C9.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(titleRect, p); p.shader = null
        drawText(c, title, titleRect.left + 7f, titleRect.centerY() + 4f, 10f, Color.WHITE, true, Paint.Align.LEFT)
        p.color = 0xFF8D8D8D.toInt(); c.drawRect(rect.left + 8, rect.top + 39, rect.left + 43, rect.top + 74, p)
        p.color = red; c.drawCircle(rect.left + 25.5f, rect.top + 56.5f, 12f, p)
        p.color = Color.WHITE; c.drawCircle(rect.left + 21f, rect.top + 53f, 2f, p)
        drawText(c, message, rect.left + 53f, rect.top + 59f, 13f, Color.BLACK, true, Paint.Align.LEFT)
        p.color = 0xFFE0E0E0.toInt(); c.drawRect(rect.left + 5, rect.bottom - 35, rect.right - 5, rect.bottom - 5, p)
    }

    private fun okRect(rect: RectF) = RectF(rect.right - 76f, rect.bottom - 30f, rect.right - 14f, rect.bottom - 9f)

    private fun drawSplit(c: Canvas, t: Float) {
        val rect = popupRect()
        val progress = ((SystemClock.uptimeMillis() - phaseStart) / 2200f).coerceIn(0f, 1f)
        val fall = progress * progress * 180f * resources.displayMetrics.density
        val fade = (1f - progress).coerceIn(0f, 1f)
        val angle = progress * 24f
        c.save()
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (fade * 255f).toInt().coerceIn(0, 255) }
        c.saveLayer(RectF(0f, 0f, widthF, heightF), fadePaint)
        c.save(); c.translate(-progress * 10f, fall); c.rotate(-angle, rect.centerX(), rect.centerY()); c.clipRect(0f, 0f, widthF / 2f, heightF); drawRetroPanel(c, rect, "virabot.exe - Warning", "What are you doing!"); c.restore()
        c.save(); c.translate(progress * 10f, fall); c.rotate(angle, rect.centerX(), rect.centerY()); c.clipRect(widthF / 2f, 0f, widthF, heightF); drawRetroPanel(c, rect, "virabot.exe - Warning", "What are you doing!"); c.restore()
        drawBinaryParticles(c, rect.centerX(), rect.centerY(), progress, fade)
        c.restore()
        c.restore()
    }

    private fun drawBinaryParticles(c: Canvas, cx: Float, cy: Float, progress: Float, fade: Float) {
        val count = if (particlesEnabled) 34 else 10
        for (i in 0 until count) {
            val seed = i * 17.17f
            val a = seed % (2f * PI.toFloat())
            val speed = 35f + (i % 7) * 12f
            val x = cx + cos(a) * (progress * speed * 1.8f)
            val y = cy + sin(a) * (progress * speed * 1.8f) + progress * progress * 70f
            val alpha = (fade * 210).toInt().coerceIn(0, 255)
            val glyph = if (i % 2 == 0) "0" else "1"
            drawText(c, glyph, x, y, 10f + (i % 3) * 2f, Color.argb(alpha, 255, 45 + i % 5 * 15, 55), true, Paint.Align.CENTER)
        }
        p.color = Color.argb((fade * 155).toInt().coerceIn(0, 255), 255, 40, 55)
        for (i in 0 until 18) {
            val a = i * 2.399f
            val d = progress * (24f + (i % 6) * 14f)
            c.drawCircle(cx + cos(a) * d, cy + sin(a) * d + progress * progress * 55f, (1f + (i % 3)) * fade, p)
        }
    }

    private fun drawText(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean, align: Paint.Align) {
        p.color = color; p.textSize = size * resources.displayMetrics.density.coerceAtMost(1.5f)
        p.typeface = Typeface.create("monospace", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = align; p.style = Paint.Style.FILL
        c.drawText(text, x, y, p)
    }

    private fun updatePhase(now: Long) {
        val elapsed = now - phaseStart
        when (phase) {
            Phase.WARNING -> if (elapsed > 5200L) {
                phase = Phase.SPLIT; phaseStart = now; play(biteSound)
            }
            Phase.SPLIT -> if (elapsed > 2200L) {
                warningsLeft--
                if (warningsLeft > 0 && !safeMode) { phase = Phase.WARNING; phaseStart = now; play(warningSound) }
                else { phase = Phase.IDLE; warningsLeft = 0 }
            }
            Phase.IDLE -> Unit
        }
    }

    private fun play(id: Int) { if (soundEnabled && id != 0) soundPool.play(id, .65f, .65f, 1, 0, 1f) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (phase == Phase.WARNING) {
            val b = okRect(popupRect())
            if (b.contains(event.x, event.y)) {
                warningsLeft--
                if (warningsLeft > 0) { phaseStart = SystemClock.uptimeMillis(); play(warningSound) }
                else { phase = Phase.IDLE; warningsLeft = 0 }
                invalidate(); return true
            }
        }
        if (safeMode) { toggleSafeMode(); return true }
        tapCount++
        tapTask?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            val count = tapCount; tapCount = 0
            if (count >= 4) {
                safeMode = true; phase = Phase.IDLE; warningsLeft = 0; particles.clear(); onSafeChanged(true)
            } else if (count == 1) startDemo()
            invalidate()
        }
        tapTask = task
        handler.postDelayed(task, 850L)
        invalidate()
        return true
    }

    private data class Particle(val x: Float, val y: Float, val vx: Float, val vy: Float, val glyph: String)
}
