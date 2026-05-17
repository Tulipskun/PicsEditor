package com.picseditor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView

class ZoomPanHandler(context: Context, private val imageView: ImageView) {

    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var mode = NONE
    private var lastX = 0f
    private var lastY = 0f
    private var currentScale = 1f

    private val minScale = 0.5f
    private val maxScale = 8f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = currentScale * factor
                if (newScale in minScale..maxScale) {
                    currentScale = newScale
                    matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                    imageView.imageMatrix = matrix
                }
                return true
            }
        })

    // เรียกหลัง layout เสร็จ เพื่อ center รูปตั้งต้น
    fun init() {
        imageView.post {
            val drawable = imageView.drawable ?: return@post
            val dw = drawable.intrinsicWidth.toFloat()
            val dh = drawable.intrinsicHeight.toFloat()
            val vw = imageView.width.toFloat()
            val vh = imageView.height.toFloat()

            val scale = minOf(vw / dw, vh / dh)
            currentScale = scale

            val tx = (vw - dw * scale) / 2f
            val ty = (vh - dh * scale) / 2f

            matrix.reset()
            matrix.postScale(scale, scale)
            matrix.postTranslate(tx, ty)
            imageView.imageMatrix = matrix
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = ZOOM
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && mode == DRAG) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    matrix.postTranslate(dx, dy)
                    imageView.imageMatrix = matrix
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = DRAG
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                mode = NONE
                snapBack()
            }
        }
        return true
    }

    private fun snapBack() {
        val drawable = imageView.drawable ?: return
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()

        matrix.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val tx = matrixValues[Matrix.MTRANS_X]
        val ty = matrixValues[Matrix.MTRANS_Y]

        val scaledW = dw * scale
        val scaledH = dh * scale

        var targetX = tx
        var targetY = ty

        if (scaledW <= vw) {
            targetX = (vw - scaledW) / 2f
        } else {
            if (tx > 0) targetX = 0f
            if (tx + scaledW < vw) targetX = vw - scaledW
        }

        if (scaledH <= vh) {
            targetY = (vh - scaledH) / 2f
        } else {
            if (ty > 0) targetY = 0f
            if (ty + scaledH < vh) targetY = vh - scaledH
        }

        if (targetX == tx && targetY == ty) return

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                matrixValues[Matrix.MTRANS_X] = tx + (targetX - tx) * f
                matrixValues[Matrix.MTRANS_Y] = ty + (targetY - ty) * f
                matrix.setValues(matrixValues)
                imageView.imageMatrix = matrix
            }
            start()
        }
    }

    fun reset() {
        init()
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}