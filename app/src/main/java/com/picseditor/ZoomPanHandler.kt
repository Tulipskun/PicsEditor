package com.picseditor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.view.GestureDetector
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
    private var lastMidX = 0f
    private var lastMidY = 0f

    private var fitScale = 1f
    private val minScaleRatio = 0.3f
    private var maxScaleAbs = 20f

    private val checkpoints = listOf(1f, 2f, 5f, 10f)
    private var checkpointIndex = 0

    private var isDoubleTapping = false
    private var snapAnimator: ValueAnimator? = null

    private fun getCurrentScale(): Float {
        matrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = getCurrentScale() * factor
                val absMin = fitScale * minScaleRatio
                if (newScale in absMin..maxScaleAbs) {
                    matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                    imageView.imageMatrix = matrix
                }
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                isDoubleTapping = true
                snapAnimator?.cancel()
                checkpointIndex = (checkpointIndex + 1) % checkpoints.size
                val targetScale = fitScale * checkpoints[checkpointIndex]
                animateScaleTo(targetScale, e.x, e.y)
                return true
            }
        })

    fun init() {
        imageView.post {
            val drawable = imageView.drawable ?: return@post
        drawable.isFilterBitmap = false
            val dw = drawable.intrinsicWidth.toFloat()
            val dh = drawable.intrinsicHeight.toFloat()
            val vw = imageView.width.toFloat()
            val vh = imageView.height.toFloat()

            fitScale = minOf(vw / dw, vh / dh)
            maxScaleAbs = vw / 6f

            val tx = (vw - dw * fitScale) / 2f
            val ty = (vh - dh * fitScale) / 2f

            matrix.reset()
            matrix.postScale(fitScale, fitScale)
            matrix.postTranslate(tx, ty)
            imageView.imageMatrix = matrix
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    lastMidX = (event.getX(0) + event.getX(1)) / 2f
                    lastMidY = (event.getY(0) + event.getY(1)) / 2f
                    mode = ZOOM
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    mode == DRAG && !scaleDetector.isInProgress -> {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        matrix.postTranslate(dx, dy)
                        imageView.imageMatrix = matrix
                        lastX = event.x
                        lastY = event.y
                    }
                    mode == ZOOM && event.pointerCount >= 2 -> {
                        val midX = (event.getX(0) + event.getX(1)) / 2f
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        matrix.postTranslate(midX - lastMidX, midY - lastMidY)
                        imageView.imageMatrix = matrix
                        lastMidX = midX
                        lastMidY = midY
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    mode = DRAG
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getX(remaining)
                    lastY = event.getY(remaining)
                }
            }
            MotionEvent.ACTION_UP -> {
                mode = NONE
                if (!isDoubleTapping) snapBack()
                isDoubleTapping = false
            }
        }
        return true
    }

    private fun animateScaleTo(targetScale: Float, pivotX: Float, pivotY: Float) {
        matrix.getValues(matrixValues)
        val startScale = matrixValues[Matrix.MSCALE_X]
        val startTx = matrixValues[Matrix.MTRANS_X]
        val startTy = matrixValues[Matrix.MTRANS_Y]

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val scale = startScale + (targetScale - startScale) * f
                val ratio = scale / startScale
                matrixValues[Matrix.MSCALE_X] = scale
                matrixValues[Matrix.MSCALE_Y] = scale
                matrixValues[Matrix.MTRANS_X] = pivotX - ratio * (pivotX - startTx)
                matrixValues[Matrix.MTRANS_Y] = pivotY - ratio * (pivotY - startTy)
                matrix.setValues(matrixValues)
                imageView.imageMatrix = matrix
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isDoubleTapping = false
                    snapBack()
                }
            })
            start()
        }
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

        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
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
        checkpointIndex = 0
        init()
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}