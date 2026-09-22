package io.github.diegog0477.zombiebox.cast.features.home.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets

/** Decorative vector illustration, never a captured screen or a consent preview. */
class PhoneIllustration(context: Context) : View(context) {
    private val ui = PhoneWidgets(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 88f, height / 156f)
        paint.color = ui.background
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(5f, 2f, 83f, 154f), 12f, 12f, paint)
        paint.color = ui.muted
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(5f, 2f, 83f, 154f), 12f, 12f, paint)
        paint.style = Paint.Style.FILL
        paint.color = ui.surface
        canvas.drawRoundRect(RectF(12f, 18f, 76f, 133f), 5f, 5f, paint)
        paint.color = ui.accent
        canvas.drawRoundRect(RectF(20f, 36f, 68f, 65f), 5f, 5f, paint)
        paint.color = ui.muted
        for (y in listOf(77f, 87f, 97f)) canvas.drawRoundRect(
            RectF(20f, y, 58f, y + 3f),
            1f,
            1f,
            paint,
        )
        canvas.drawRoundRect(RectF(34f, 9f, 54f, 11f), 1f, 1f, paint)
        canvas.drawRoundRect(RectF(32f, 143f, 56f, 146f), 1f, 1f, paint)
        canvas.restore()
    }
}
