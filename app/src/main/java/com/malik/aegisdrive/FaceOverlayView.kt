package com.malik.aegisdrive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.min

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: FaceLandmarkerResult? = null
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 2f
        style = Paint.Style.FILL
    }

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f

    fun setResults(
        faceLandmarkerResult: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int
    ) {
        results = faceLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        results?.let { faceLandmarkerResult ->
            for (landmarks in faceLandmarkerResult.faceLandmarks()) {
                for (landmark in landmarks) {
                    canvas.drawPoint(
                        landmark.x() * imageWidth * scaleFactor,
                        landmark.y() * imageHeight * scaleFactor,
                        pointPaint
                    )
                }
            }
        }
    }
}
