package com.simoesctt.phasevib

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.ArrayDeque

/**
 * Scrolling waveform plot for 3-channel sensor data.
 */
class LivePlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val maxSamples = 600
    private val ch1 = ArrayDeque<Float>(maxSamples)
    private val ch2 = ArrayDeque<Float>(maxSamples)
    private val ch3 = ArrayDeque<Float>(maxSamples)

    private val paints = arrayOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B388FF"); strokeWidth = 3f; style = Paint.Style.STROKE
        },
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#69F0AE"); strokeWidth = 3f; style = Paint.Style.STROKE
        },
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF80AB"); strokeWidth = 3f; style = Paint.Style.STROKE
        }
    )
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#311B92"); strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9575CD"); textSize = 22f
    }

    fun push(x: Float, y: Float, z: Float) {
        if (ch1.size >= maxSamples) ch1.removeFirst()
        if (ch2.size >= maxSamples) ch2.removeFirst()
        if (ch3.size >= maxSamples) ch3.removeFirst()
        ch1.addLast(x); ch2.addLast(y); ch3.addLast(z)
        invalidate()
    }

    fun clear() {
        ch1.clear(); ch2.clear(); ch3.clear(); invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 20f

        // Background grid
        for (i in 0..4) {
            val y = padding + i * (h - 2 * padding) / 4
            canvas.drawLine(padding, y, w - padding, y, gridPaint)
        }
        for (i in 0..5) {
            val x = padding + i * (w - 2 * padding) / 5
            canvas.drawLine(x, padding, x, h - padding, gridPaint)
        }

        if (ch1.isEmpty()) {
            canvas.drawText("(no data)", padding + 8, h / 2, textPaint)
            return
        }

        // Normalize to per-channel range
        val all = mutableListOf<Float>()
        all.addAll(ch1); all.addAll(ch2); all.addAll(ch3)
        val mn = all.minOrNull() ?: 0f
        val mx = all.maxOrNull() ?: 1f
        val range = if (mx > mn) mx - mn else 1f

        val dx = (w - 2 * padding) / (maxSamples - 1)
        val usableH = h - 2 * padding

        drawSeries(canvas, ch1, mn, range, padding, dx, usableH, paints[0])
        drawSeries(canvas, ch2, mn, range, padding, dx, usableH, paints[1])
        drawSeries(canvas, ch3, mn, range, padding, dx, usableH, paints[2])
    }

    private fun drawSeries(
        canvas: Canvas,
        data: ArrayDeque<Float>,
        mn: Float,
        range: Float,
        padding: Float,
        dx: Float,
        usableH: Float,
        paint: Paint
    ) {
        val path = Path()
        var i = 0
        for (v in data) {
            val x = padding + i * dx
            val y = padding + usableH - ((v - mn) / range) * usableH
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i++
        }
        canvas.drawPath(path, paint)
    }
}
