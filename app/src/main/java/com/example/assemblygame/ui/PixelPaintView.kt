package com.example.assemblygame.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.assemblygame.game.AssemblyVM

/** A tappable 16x16 grid: each tap cycles a cell '.' -> '#' -> '+' -> '.'. */
class PixelPaintView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val n = AssemblyVM.CANVAS_SIZE
    private var pixels = IntArray(n * n)
    private var listener: ((List<String>) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setOnPatternChanged(l: (List<String>) -> Unit) { listener = l }

    fun setPattern(source: List<String>) {
        pixels = IntArray(n * n)
        for (y in 0 until minOf(source.size, n)) {
            val row = source[y]
            for (x in 0 until minOf(row.length, n)) {
                pixels[y * n + x] = when (row[x]) { '#' -> 1; '+' -> 2; else -> 0 }
            }
        }
        invalidate()
    }

    fun clear() {
        pixels = IntArray(n * n)
        invalidate()
        listener?.invoke(pattern())
    }

    fun pattern(): List<String> {
        val chars = charArrayOf('.', '#', '+')
        return List(n) { y -> CharArray(n) { x -> chars[pixels[y * n + x]] }.concatToString() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val size = minOf(width, height).toFloat()
            val ox = (width - size) / 2f
            val oy = (height - size) / 2f
            val cell = size / n
            val x = ((event.x - ox) / cell).toInt()
            val y = ((event.y - oy) / cell).toInt()
            if (x in 0 until n && y in 0 until n) {
                pixels[y * n + x] = (pixels[y * n + x] + 1) % 3
                invalidate()
                listener?.invoke(pattern())
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val ox = (width - size) / 2f
        val oy = (height - size) / 2f
        val cell = size / n
        val gap = cell * 0.12f
        for (y in 0 until n) {
            for (x in 0 until n) {
                paint.color = when (pixels[y * n + x]) {
                    1 -> 0xFF4ADE80.toInt()
                    2 -> 0xFF60A5FA.toInt()
                    else -> 0xFF1C212B.toInt()
                }
                canvas.drawRoundRect(
                    ox + x * cell + gap, oy + y * cell + gap,
                    ox + (x + 1) * cell - gap, oy + (y + 1) * cell - gap,
                    gap, gap, paint
                )
            }
        }
    }
}
