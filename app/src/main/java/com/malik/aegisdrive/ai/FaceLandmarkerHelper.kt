package com.malik.aegisdrive.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    private val context: Context,
    private val listener: LandmarkerListener? = null
) {
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(Delegate.GPU)

        val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPipe face landmarker failed to initialize. Error: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        detectAsync(mpImage, frameTime)
    }

    fun detectBitmap(bitmap: Bitmap) {
        val frameTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(bitmap).build()
        detectAsync(mpImage, frameTime)
    }

    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(
        result: FaceLandmarkerResult,
        inputImage: MPImage
    ) {
        listener?.onResults(result, inputImage)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        listener?.onError(error.message ?: "An unknown error has occurred")
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: FaceLandmarkerResult, inputImage: MPImage)
    }

    companion object {
        private const val TAG = "FaceLandmarkerHelper"
    }
}
