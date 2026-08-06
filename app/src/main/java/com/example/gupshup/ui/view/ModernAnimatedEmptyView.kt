package com.example.gupshup.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.gupshup.R

/**
 * A custom modern animated view for empty states in GupShup.
 * Renders continuous 60fps expanding radar pulse rings and a floating, breathing icon.
 */
class ModernAnimatedEmptyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var iconDrawable: Drawable? = null
    private var primaryColor: Int = Color.parseColor("#00A884")

    private val ringPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val ringPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var pulseFraction = 0f
    private var floatOffsetY = 0f
    private var iconScale = 1.0f

    private var pulseAnimator: ValueAnimator? = null
    private var floatAnimator: ValueAnimator? = null

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ModernAnimatedEmptyView)
        val drawableId = typedArray.getResourceId(R.styleable.ModernAnimatedEmptyView_iconSrc, 0)
        if (drawableId != 0) {
            iconDrawable = ContextCompat.getDrawable(context, drawableId)
        }
        primaryColor = typedArray.getColor(R.styleable.ModernAnimatedEmptyView_pulseColor, Color.parseColor("#00A884"))
        typedArray.recycle()

        setupAnimators()
    }

    fun setIconResource(resId: Int) {
        iconDrawable = ContextCompat.getDrawable(context, resId)
        invalidate()
    }

    private fun setupAnimators() {
        // Continuous Radar Pulse Ring Animation
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                pulseFraction = anim.animatedValue as Float
                invalidate()
            }
        }

        // Floating Bobbing & Breathing Animation for Icon
        floatAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                floatOffsetY = -12f * fraction
                iconScale = 1.0f + (0.08f * fraction)
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator?.start()
        floatAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        floatAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = (Math.min(width, height) / 2f) * 0.85f

        // 1. Inner Soft Glow Circle
        val glowRadius = maxRadius * 0.35f
        innerGlowPaint.color = primaryColor
        innerGlowPaint.alpha = 25
        canvas.drawCircle(cx, cy, glowRadius, innerGlowPaint)

        // 2. Pulse Ring 1 (expanding from 30% to 90% of max radius)
        val radius1 = (0.3f + 0.6f * pulseFraction) * maxRadius
        val alpha1 = ((1f - pulseFraction) * 160).toInt().coerceIn(0, 255)
        ringPaint1.color = primaryColor
        ringPaint1.alpha = alpha1
        canvas.drawCircle(cx, cy, radius1, ringPaint1)

        // 3. Pulse Ring 2 (offset by 50% phase)
        val phase2 = (pulseFraction + 0.5f) % 1.0f
        val radius2 = (0.3f + 0.6f * phase2) * maxRadius
        val alpha2 = ((1f - phase2) * 120).toInt().coerceIn(0, 255)
        ringPaint2.color = primaryColor
        ringPaint2.alpha = alpha2
        canvas.drawCircle(cx, cy, radius2, ringPaint2)

        // 4. Floating Center Icon
        iconDrawable?.let { drawable ->
            canvas.save()
            val iconSize = (Math.min(width, height) * 0.38f * iconScale).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f + floatOffsetY).toInt()
            val right = left + iconSize
            val bottom = top + iconSize

            drawable.setBounds(left, top, right, bottom)
            drawable.setTint(primaryColor)
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
