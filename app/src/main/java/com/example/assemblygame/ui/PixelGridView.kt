package com.example.assemblygame.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.assemblygame.game.AssemblyVM

/** Draws the VM's 16x16 canvas as rounded pixel tiles. */
class PixelGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val n = AssemblyVM.CANVAS_SIZE
    private var pixels = IntArray(n * n)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setPixels(source: IntArray) {
        if (source.size == pixels.size) {
            System.arraycopy(source, 0, pixels, 0, source.size)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val ox = (width - size) / 2f
        val oy = (height - size) / 2f
        val cell = size / n
        val gap = cell * 0.12f
        for (y in 0 until n) {
            for (x in 0 until n) {
                paint.color = when (pixels[y * n + x]) {
                    1 -> 0xFF4ADE80.toInt()   // ink
                    2 -> 0xFF60A5FA.toInt()   // accent
                    else -> 0xFF1C212B.toInt() // empty
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
