package com.reclaim

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/** A small eye drawn for Reclaim. It floats and blinks. No outside artwork. */
class EyeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blink = 0f
    private var bob = 0f
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { value ->
                val t = value.animatedValue as Float
                blink = blinkAmount(t)
                bob = sin(t * 2.0 * PI).toFloat()
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f + bob * height * 0.05f
        val radiusX = width * 0.36f
        val open = 1f - 0.92f * blink
        val radiusY = height * 0.26f * open

        paint.style = Paint.Style.FILL
        paint.color = 0xFFF6F3EE.toInt()
        canvas.drawOval(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * 0.045f
        paint.color = 0xFF1B2430.toInt()
        canvas.drawOval(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY, paint)

        if (blink < 0.82f) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFF1B2430.toInt()
            val pupil = radiusX * 0.42f * (1f - blink)
            canvas.drawCircle(cx + radiusX * 0.08f, cy, pupil, paint)
            paint.color = 0xFFF6F3EE.toInt()
            canvas.drawCircle(cx + pupil * 0.35f, cy - pupil * 0.35f, pupil * 0.28f, paint)
        }
    }

    private fun blinkAmount(t: Float): Float {
        return when {
            t < 0.78f -> 0f
            t < 0.86f -> (t - 0.78f) / 0.08f
            t < 0.94f -> 1f - (t - 0.86f) / 0.08f
            else -> 0f
        }
    }
}
