package com.picseditor

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

class CropOverlayView(context: Context, private val imageView: ImageView) : View(context) {

    var cropBitmap = RectF()
    var lockedRatio: Float? = null
    var onChanged: ((RectF) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private enum class Handle { NONE, MOVE, TL, T, TR, R, BR, B, BL, L }
    private var active = Handle.NONE
    private val last = PointF()
    private val dens = context.resources.displayMetrics.density

    private val overlayPaint = Paint().apply { color = 0xBB000000.toInt() }
    private val borderPaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 1.5f * dens; isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 3f * dens; isAntiAlias = true; strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint().apply {
        color = 0xCCFFFFFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val gridPaint = Paint().apply {
        color = 0x33FFFFFF; style = Paint.Style.STROKE; strokeWidth = 0.8f * dens
    }

    private val handleLen get() = 20f * dens
    private val touchSlop get() = 32f * dens
    private val minPx = 20f

    private fun screenRect(): RectF {
        val pts = floatArrayOf(cropBitmap.left, cropBitmap.top, cropBitmap.right, cropBitmap.bottom)
        imageView.imageMatrix.mapPoints(pts)
        val iv = IntArray(2); imageView.getLocationInWindow(iv)
        val me = IntArray(2); getLocationInWindow(me)
        val dx = (iv[0] - me[0]).toFloat(); val dy = (iv[1] - me[1]).toFloat()
        return RectF(pts[0]+dx, pts[1]+dy, pts[2]+dx, pts[3]+dy)
    }

    private fun toBitmapDelta(dx: Float, dy: Float): FloatArray {
        val inv = Matrix(); imageView.imageMatrix.invert(inv)
        val v = floatArrayOf(dx, dy); inv.mapVectors(v); return v
    }

    private fun bitmapBounds() = imageView.drawable?.let {
        RectF(0f, 0f, it.intrinsicWidth.toFloat(), it.intrinsicHeight.toFloat())
    } ?: RectF()

    override fun onDraw(canvas: Canvas) {
        val sr = screenRect()
        val w = width.toFloat(); val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, sr.top, overlayPaint)
        canvas.drawRect(0f, sr.bottom, w, h, overlayPaint)
        canvas.drawRect(0f, sr.top, sr.left, sr.bottom, overlayPaint)
        canvas.drawRect(sr.right, sr.top, w, sr.bottom, overlayPaint)

        val tw = sr.width()/3; val th = sr.height()/3
        canvas.drawLine(sr.left+tw, sr.top, sr.left+tw, sr.bottom, gridPaint)
        canvas.drawLine(sr.left+tw*2, sr.top, sr.left+tw*2, sr.bottom, gridPaint)
        canvas.drawLine(sr.left, sr.top+th, sr.right, sr.top+th, gridPaint)
        canvas.drawLine(sr.left, sr.top+th*2, sr.right, sr.top+th*2, gridPaint)

        canvas.drawRect(sr, borderPaint)

        val hl = handleLen
        canvas.drawLine(sr.left, sr.top+hl, sr.left, sr.top, handlePaint)
        canvas.drawLine(sr.left, sr.top, sr.left+hl, sr.top, handlePaint)
        canvas.drawLine(sr.right-hl, sr.top, sr.right, sr.top, handlePaint)
        canvas.drawLine(sr.right, sr.top, sr.right, sr.top+hl, handlePaint)
        canvas.drawLine(sr.left, sr.bottom-hl, sr.left, sr.bottom, handlePaint)
        canvas.drawLine(sr.left, sr.bottom, sr.left+hl, sr.bottom, handlePaint)
        canvas.drawLine(sr.right-hl, sr.bottom, sr.right, sr.bottom, handlePaint)
        canvas.drawLine(sr.right, sr.bottom, sr.right, sr.bottom-hl, handlePaint)

        val dot = 5f * dens
        canvas.drawCircle(sr.centerX(), sr.top, dot, dotPaint)
        canvas.drawCircle(sr.centerX(), sr.bottom, dot, dotPaint)
        canvas.drawCircle(sr.left, sr.centerY(), dot, dotPaint)
        canvas.drawCircle(sr.right, sr.centerY(), dot, dotPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                active = hitTest(e.x, e.y)
                last.set(e.x, e.y)
                return active != Handle.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (active == Handle.NONE) return false
                val v = toBitmapDelta(e.x - last.x, e.y - last.y)
                val dbx = v[0]; val dby = v[1]
                val b = bitmapBounds(); val r = lockedRatio
                when (active) {
                    Handle.MOVE -> {
                        val nx = (cropBitmap.left + dbx).coerceIn(b.left, b.right - cropBitmap.width())
                        val ny = (cropBitmap.top + dby).coerceIn(b.top, b.bottom - cropBitmap.height())
                        cropBitmap.offsetTo(nx, ny)
                    }
                    Handle.TL -> {
                        cropBitmap.left = (cropBitmap.left + dbx).coerceIn(b.left, cropBitmap.right - minPx)
                        cropBitmap.top  = (cropBitmap.top  + dby).coerceIn(b.top,  cropBitmap.bottom - minPx)
                        r?.let { cropBitmap.top = cropBitmap.bottom - cropBitmap.width() / it }
                    }
                    Handle.TR -> {
                        cropBitmap.right = (cropBitmap.right + dbx).coerceIn(cropBitmap.left + minPx, b.right)
                        cropBitmap.top   = (cropBitmap.top   + dby).coerceIn(b.top, cropBitmap.bottom - minPx)
                        r?.let { cropBitmap.top = cropBitmap.bottom - cropBitmap.width() / it }
                    }
                    Handle.BL -> {
                        cropBitmap.left   = (cropBitmap.left   + dbx).coerceIn(b.left, cropBitmap.right - minPx)
                        cropBitmap.bottom = (cropBitmap.bottom + dby).coerceIn(cropBitmap.top + minPx, b.bottom)
                        r?.let { cropBitmap.bottom = cropBitmap.top + cropBitmap.width() / it }
                    }
                    Handle.BR -> {
                        cropBitmap.right  = (cropBitmap.right  + dbx).coerceIn(cropBitmap.left + minPx, b.right)
                        cropBitmap.bottom = (cropBitmap.bottom + dby).coerceIn(cropBitmap.top + minPx, b.bottom)
                        r?.let { cropBitmap.bottom = cropBitmap.top + cropBitmap.width() / it }
                    }
                    Handle.T -> cropBitmap.top    = (cropBitmap.top    + dby).coerceIn(b.top, cropBitmap.bottom - minPx)
                    Handle.B -> cropBitmap.bottom = (cropBitmap.bottom + dby).coerceIn(cropBitmap.top + minPx, b.bottom)
                    Handle.L -> cropBitmap.left   = (cropBitmap.left   + dbx).coerceIn(b.left, cropBitmap.right - minPx)
                    Handle.R -> cropBitmap.right  = (cropBitmap.right  + dbx).coerceIn(cropBitmap.left + minPx, b.right)
                    else -> {}
                }
                last.set(e.x, e.y)
                invalidate()
                onChanged?.invoke(RectF(cropBitmap))
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (active != Handle.NONE) onDragEnd?.invoke()
                active = Handle.NONE
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): Handle {
        val sr = screenRect(); val s = touchSlop
        fun near(a: Float, b: Float) = Math.abs(a-b) < s
        if (near(x, sr.left)    && near(y, sr.top))       return Handle.TL
        if (near(x, sr.right)   && near(y, sr.top))       return Handle.TR
        if (near(x, sr.left)    && near(y, sr.bottom))    return Handle.BL
        if (near(x, sr.right)   && near(y, sr.bottom))    return Handle.BR
        if (near(x, sr.centerX()) && near(y, sr.top))     return Handle.T
        if (near(x, sr.centerX()) && near(y, sr.bottom))  return Handle.B
        if (near(x, sr.left)    && near(y, sr.centerY())) return Handle.L
        if (near(x, sr.right)   && near(y, sr.centerY())) return Handle.R
        if (sr.contains(x, y)) return Handle.MOVE
        return Handle.NONE
    }

    fun setCrop(rect: RectF) { cropBitmap.set(rect); invalidate() }
}
