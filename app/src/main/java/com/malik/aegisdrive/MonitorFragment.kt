package com.malik.aegisdrive

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

@SuppressLint("SetTextI18n") 
class MonitorFragment : Fragment() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var tvTimer: TextView
    private lateinit var tvFPS: TextView
    private lateinit var tvLiveEar: TextView
    private lateinit var tvLiveMar: TextView
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvDetectionIcon: TextView
    private lateinit var tvDetectionLabel: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvEyeState: TextView
    private lateinit var tvDrowsiness: TextView
    private lateinit var tvAlertCount: TextView
    private lateinit var btnStopMonitor: MaterialButton
    private lateinit var btnMuteAlarm: MaterialButton
    private lateinit var statusOverlay: MaterialCardView
    private lateinit var liveBadgeCard: MaterialCardView
    private lateinit var tvLiveText: TextView
    private lateinit var tvSafetyScore: TextView
    private lateinit var pbSafetyScore: ProgressBar

    private var cameraExecutor: ExecutorService? = null
    private var tfliteInterpreter: Interpreter? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var mediaPlayer: MediaPlayer? = null
    private var isMonitoring = false
    private var safetyScore = 100f
    
    private var alertCount = 0
    private var isAlarmActive = false 
    private var isManuallyMuted = false 
    private var muteTimestamp = 0L
    private var framesWithoutFace = 0
    private var closedEyeFrames = 0
    private var openEyeFrames = 0

    private val sequenceLength = 30
    private val frameSequence = ArrayDeque<FloatArray>(sequenceLength)
    private val predictionHistory = ArrayDeque<Int>(5) 
    
    private val rightEyeIdx = intArrayOf(33, 160, 158, 133, 153, 144)
    private val leftEyeIdx = intArrayOf(362, 385, 387, 263, 373, 380)
    private val lipsIdx = intArrayOf(78, 308, 13, 14)

    private val classLabels = arrayOf("Normal", "Drowsiness (Eye Close)", "Yawning")
    private val lstmModelFile = "aegis_drive_model.tflite"

    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private val timerHandler = Handler(Looper.getMainLooper())
    private var elapsedSeconds = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            elapsedSeconds++
            val h = elapsedSeconds / 3600
            val m = (elapsedSeconds % 3600) / 60
            val s = elapsedSeconds % 60
            tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
            timerHandler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val TAG = "AegisDrive"
        private const val CAMERA_PERMISSION_CODE = 200
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        
        pbSafetyScore.max = 100 

        // 🚀 SAFE PROGRAMMATIC ADDITION: Prevent duplicates and lag
        val parent = cameraPreview.parent as ViewGroup
        var existingOverlay: FaceOverlayView? = null
        for (i in 0 until parent.childCount) {
            if (parent.getChildAt(i) is FaceOverlayView) {
                existingOverlay = parent.getChildAt(i) as FaceOverlayView
                break
            }
        }

        if (existingOverlay != null) {
            faceOverlay = existingOverlay
        } else {
            faceOverlay = FaceOverlayView(requireContext())
            faceOverlay.layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            parent.addView(faceOverlay)
        }
        faceOverlay.bringToFront()
        faceOverlay.elevation = 100f

        setupAudioEngine()
        loadAIModels()

        isMonitoring = false
        setUIMonitoringState(false)
        btnMuteAlarm.visibility = View.GONE

        if (hasCameraPermission()) startCamera() else requestCameraPermission()

        btnStopMonitor.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission()
                return@setOnClickListener
            }
            if (isMonitoring) stopMonitoring() else startMonitoring()
        }

        btnMuteAlarm.setOnClickListener {
            isManuallyMuted = true 
            muteTimestamp = System.currentTimeMillis() 
            stopAudioAlarm()
        }
    }

    private fun setupAudioEngine() {
        try {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(requireContext(), RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(requireContext(), ringtoneUri)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) 
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true 
                prepare()
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Ringtone Engine Error: ${e.message}")
            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer?.setDataSource(requireContext(), alertUri)
                mediaPlayer?.prepare()
            } catch (e2: Exception) { Log.e(TAG, "Complete Audio Failure") }
        }
    }

    private var lastVibrationTime = 0L

    private fun triggerVibration(duration: Long) {
        try {
            val ctx = context ?: return
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (vibrator.hasVibrator()) {
                // 🚀 HIGH PRIORITY: Use USAGE_ALARM to bypass certain system restrictions and ensure visibility
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE),
                        audioAttributes
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration Error: ${e.message}")
        }
    }

    private fun playAudioAlarm() {
        val activity = activity ?: return
        if (!isAdded) return
        
        val prefs = activity.getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("alert_sound", true)
        val vibrationEnabled = prefs.getBoolean("vibration", true)

        if (vibrationEnabled) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVibrationTime > 1500) { // Vibrate every 1.5s during danger
                triggerVibration(1000) // 🚀 Increased to 1s for better feedback
                lastVibrationTime = currentTime
            }
        }

        if (!isAlarmActive) {
            isAlarmActive = true
            if (soundEnabled) mediaPlayer?.start()
            
            activity?.runOnUiThread { 
                btnMuteAlarm.visibility = View.VISIBLE 
                alertCount++
                tvAlertCount.text = alertCount.toString()
            }
        } else if (soundEnabled && mediaPlayer?.isPlaying == false && !isManuallyMuted) {
            mediaPlayer?.start()
        }
    }

    private fun stopAudioAlarm() {
        isAlarmActive = false
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
        }
        activity?.runOnUiThread { btnMuteAlarm.visibility = View.GONE }
    }

    override fun onResume() {
        super.onResume()
        if (!isMonitoring) setUIMonitoringState(false)
    }

    override fun onPause() {
        super.onPause()
        stopMonitoring()
    }

    override fun onStop() {
        super.onStop()
        if (isMonitoring) stopMonitoring()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopMonitoring()
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        tfliteInterpreter?.close()
        faceLandmarker?.close()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startMonitoring() {
        isMonitoring = true
        elapsedSeconds = 0
        alertCount = 0
        safetyScore = 100f
        frameCount = 0
        lastFpsTime = System.currentTimeMillis()
        isManuallyMuted = false
        isAlarmActive = false
        closedEyeFrames = 0
        openEyeFrames = 0
        framesWithoutFace = 0
        frameSequence.clear() 
        predictionHistory.clear()
        faceOverlay.clear()

        tvAlertCount.text = "0"
        tvTimer.text = "00:00:00"

        updateSafetyUI()
        setUIMonitoringState(true)

        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.post(timerRunnable)
    }

    private fun stopMonitoring() {
        if (!isMonitoring) return
        
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val finalScore = safetyScore.toInt()
        val totalAlerts = alertCount
        val durationSeconds = elapsedSeconds
        val focusLevel = (finalScore - (totalAlerts * 4)).coerceIn(0, 100)

        val sessionData = hashMapOf(
            "userId" to uid,
            "score" to finalScore,
            "alerts" to totalAlerts,
            "duration" to durationSeconds,
            "focusLevel" to focusLevel,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "dateString" to java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("DriveSessions")
            .add(sessionData)
            .addOnSuccessListener { 
                Log.d("Firebase", "DriveSession Saved") 
                // 🚀 Update Global Lifetime Analytics
                updateLifetimeAnalytics(uid, finalScore, durationSeconds)
            }
            .addOnFailureListener { e -> Log.e("Firebase", "WRITE FAILED: Check Security Rules", e) }

        isMonitoring = false
        timerHandler.removeCallbacks(timerRunnable)
        setUIMonitoringState(false)
        stopAudioAlarm()
        btnMuteAlarm.visibility = View.GONE
        faceOverlay.clear()
    }

    private fun updateLifetimeAnalytics(uid: String, sessionScore: Int, sessionDuration: Int) {
        val db = FirebaseFirestore.getInstance()
        val analyticsRef = db.collection("SystemAnalytics").document(uid)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(analyticsRef)
            
            val currentDrives = if (snapshot.exists()) snapshot.getLong("totalDrives") ?: 0L else 0L
            val currentDuration = if (snapshot.exists()) snapshot.getLong("totalDuration") ?: 0L else 0L
            val currentScoreSum = if (snapshot.exists()) snapshot.getLong("lifetimeScoreSum") ?: 0L else 0L
            
            val updates = mapOf(
                "totalDrives" to currentDrives + 1,
                "totalDuration" to currentDuration + sessionDuration,
                "lifetimeScoreSum" to currentScoreSum + sessionScore
            )
            
            transaction.set(analyticsRef, updates, com.google.firebase.firestore.SetOptions.merge())
        }.addOnFailureListener { e ->
            Log.e("AegisAnalytics", "Failed to update lifetime stats", e)
        }
    }

    private fun setUIMonitoringState(isActive: Boolean) {
        if (isActive) {
            btnStopMonitor.text = "STOP MONITORING"
            btnStopMonitor.setTextColor("#EF5350".toColorInt())
            btnStopMonitor.backgroundTintList = ColorStateList.valueOf("#CC0D1B26".toColorInt())
            btnStopMonitor.strokeWidth = 2
            btnStopMonitor.strokeColor = ColorStateList.valueOf("#EF5350".toColorInt())
            liveBadgeCard.setCardBackgroundColor(ColorStateList.valueOf("#AA22C55E".toColorInt()))
            tvLiveText.text = "LIVE"
            setStatus("● SAFE", "#6ABF69")
        } else {
            btnStopMonitor.text = "BEGIN MONITORING"
            btnStopMonitor.setTextColor("#0F172A".toColorInt())
            btnStopMonitor.backgroundTintList = ColorStateList.valueOf("#38BDF8".toColorInt())
            btnStopMonitor.strokeWidth = 0
            liveBadgeCard.setCardBackgroundColor(ColorStateList.valueOf("#AAEF4444".toColorInt()))
            tvLiveText.text = "OFFLINE"
            setStatus("● STANDBY", "#6D8196")
            tvDetectionIcon.text = "–"
            tvDetectionLabel.text = "Waiting..."
            tvDetectionLabel.setTextColor("#6D8196".toColorInt())
            tvConfidence.text = "–"
            tvConfidence.setTextColor("#6D8196".toColorInt())
            tvEyeState.text = "–"
            tvEyeState.setTextColor("#6D8196".toColorInt())
            tvDrowsiness.text = "–"
            tvDrowsiness.setTextColor("#6D8196".toColorInt())
            tvLiveEar.text = "EAR: 0.00"
            tvLiveMar.text = "MAR: 0.00"
            tvFPS.text = "0 FPS"
            tvTimer.text = "00:00:00"
            // 🚀 SENIOR FIX: Do NOT reset the persistent safetyScore here.
            // Only update the local UI, let the last session score persist in SharedPreferences.
            val scoreInt = safetyScore.toInt()
            tvSafetyScore.text = "$scoreInt%"
            pbSafetyScore.progress = scoreInt
        }
    }

    private fun startCamera() {
        if (cameraExecutor == null || cameraExecutor!!.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 🚀 SMOOTH: Your original Resolution Selector logic
                val previewResolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder().setResolutionSelector(previewResolution).build().also { it.setSurfaceProvider(cameraPreview.surfaceProvider) }

                val analysisResolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis -> analysis.setAnalyzer(cameraExecutor!!) { imageProxy -> analyzeFrame(imageProxy) } }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) { Log.e(TAG, "Camera bind failed: ${e.message}") }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (!isMonitoring) {
            imageProxy.close()
            return
        }

        try {
            frameCount++
            val now = System.currentTimeMillis()
            if (now - lastFpsTime >= 1000) {
                val fps = frameCount
                frameCount = 0
                lastFpsTime = now
                activity?.runOnUiThread { tvFPS.text = "$fps FPS" }
            }

            val rawBitmap = imageProxy.toBitmap()
            val argbBitmap = rawBitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(argbBitmap, 0, 0, argbBitmap.width, argbBitmap.height, matrix, true)
            
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            val result = faceLandmarker?.detect(mpImage)

            if (result != null && result.faceLandmarks().isNotEmpty()) {
                framesWithoutFace = 0 
                
                val landmarks = result.faceLandmarks()[0]
                val rightEyePoints = rightEyeIdx.map { landmarks[it] }
                val leftEyePoints = leftEyeIdx.map { landmarks[it] }
                val lipPoints = lipsIdx.map { landmarks[it] }

                val rightEar = calculateEAR(rightEyePoints)
                val leftEar = calculateEAR(leftEyePoints)
                val avgEar = (rightEar + leftEar) / 2.0f
                val mar = calculateMAR(lipPoints)

                activity?.runOnUiThread {
                    tvLiveEar.text = String.format(Locale.US, "EAR: %.2f", avgEar)
                    tvLiveMar.text = String.format(Locale.US, "MAR: %.2f", mar)
                }

                val dynamicEarThreshold = if (mar > 0.40f) 0.15f else 0.20f
                
                if (avgEar < dynamicEarThreshold) {
                    closedEyeFrames++
                    openEyeFrames = 0
                } else {
                    openEyeFrames++
                    closedEyeFrames = 0
                }

                activity?.runOnUiThread {
                    if (closedEyeFrames >= 2) {
                        tvEyeState.text = "Closed"
                        tvEyeState.setTextColor("#EF5350".toColorInt()) 
                    } else if (openEyeFrames >= 2) {
                        tvEyeState.text = "Open"
                        tvEyeState.setTextColor("#6ABF69".toColorInt()) 
                    }
                }

                faceOverlay.updateData(leftEyePoints, rightEyePoints, lipPoints, rotatedBitmap.width, rotatedBitmap.height)

                if (frameSequence.size == sequenceLength) frameSequence.removeFirst()
                frameSequence.addLast(floatArrayOf(avgEar, mar))

                if (frameSequence.size == sequenceLength) {
                    runLSTMInference(avgEar, mar)
                } else {
                    activity?.runOnUiThread {
                        tvDetectionLabel.text = "Tracking Face..."
                        tvDetectionLabel.setTextColor("#38BDF8".toColorInt())
                        tvConfidence.text = "${frameSequence.size}/30"
                        tvConfidence.setTextColor("#38BDF8".toColorInt())
                    }
                }
            } else {
                framesWithoutFace++
                if (framesWithoutFace > 5) {
                    faceOverlay.clear() 
                    activity?.runOnUiThread {
                        tvDetectionLabel.text = "No Face Detected"
                        tvDetectionLabel.setTextColor("#EF5350".toColorInt())
                        tvConfidence.text = "-"
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Analysis error: ${e.message}")
        } finally { imageProxy.close() }
    }

    private fun runLSTMInference(currentEar: Float, currentMar: Float) {
        val interpreter = tfliteInterpreter ?: return
        try {
            val inputTensor = interpreter.getInputTensor(0)
            val expectedInputFloats = inputTensor.numElements() 
            val outputTensor = interpreter.getOutputTensor(0)
            val expectedOutputFloats = outputTensor.numElements()

            val inputBuffer = ByteBuffer.allocateDirect(expectedInputFloats * 4).apply { order(ByteOrder.nativeOrder()) }
            val outputBuffer = ByteBuffer.allocateDirect(expectedOutputFloats * 4).apply { order(ByteOrder.nativeOrder()) }

            val flatList = mutableListOf<Float>()
            for (frame in frameSequence) {
                flatList.add(frame[0]) 
                flatList.add(frame[1]) 
            }

            for (i in 0 until expectedInputFloats) {
                if (i < flatList.size) inputBuffer.putFloat(flatList[i]) else inputBuffer.putFloat(0f) 
            }
            inputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val scores = FloatArray(expectedOutputFloats)
            outputBuffer.asFloatBuffer().get(scores)

            val rawClassIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
            val confidence = (scores[rawClassIdx] * 100).toInt().coerceIn(0, 100)

            var finalClassIdx = rawClassIdx
            if (finalClassIdx == 2 && currentMar < 0.35f) finalClassIdx = 0 
            if (currentEar < 0.15f) finalClassIdx = 1 

            if (predictionHistory.size == 5) predictionHistory.removeFirst()
            predictionHistory.addLast(finalClassIdx)
            val smoothedClassIdx = predictionHistory.groupBy { it }.maxByOrNull { it.value.size }?.key ?: finalClassIdx

            activity?.runOnUiThread { updateUI(smoothedClassIdx, confidence) }
        } catch (e: Exception) { Log.e(TAG, "LSTM Inference failed: ${e.message}") }
    }

    private fun distance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun calculateEAR(eye: List<NormalizedLandmark>): Float {
        val distA = distance(eye[1], eye[5])
        val distB = distance(eye[2], eye[4])
        val distC = distance(eye[0], eye[3])
        return if (distC == 0f) 0f else (distA + distB) / (2.0f * distC)
    }

    private fun calculateMAR(lips: List<NormalizedLandmark>): Float {
        val distA = distance(lips[2], lips[3]) 
        val distC = distance(lips[0], lips[1]) 
        return if (distC == 0f) 0f else distA / distC
    }

    private fun updateUI(labelIndex: Int, confidence: Int) {
        if (!isMonitoring) return
        tvDetectionLabel.text = classLabels[labelIndex]
        tvConfidence.text = "$confidence%"

        when (labelIndex) {
            0 -> { 
                tvDetectionIcon.text = "👁"
                tvDetectionLabel.setTextColor("#6ABF69".toColorInt())
                tvConfidence.setTextColor("#6ABF69".toColorInt())
                tvDrowsiness.text = "None"
                tvDrowsiness.setTextColor("#6ABF69".toColorInt())
                setStatus("● SAFE", "#6ABF69")
                safetyScore = minOf(100f, safetyScore + 1.0f) 
            }
            1 -> { 
                tvDetectionIcon.text = "😴"
                tvDetectionLabel.setTextColor("#EF5350".toColorInt())
                tvConfidence.setTextColor("#EF5350".toColorInt())
                tvDrowsiness.text = "Drowsy" 
                tvDrowsiness.setTextColor("#EF5350".toColorInt())
                setStatus("● DANGER", "#EF5350")
                safetyScore = maxOf(0f, safetyScore - 1.5f) 
            }
            2 -> { 
                tvDetectionIcon.text = "🥱"
                tvDetectionLabel.setTextColor("#FFB74D".toColorInt())
                tvConfidence.setTextColor("#FFB74D".toColorInt())
                tvDrowsiness.text = "Yawning" 
                tvDrowsiness.setTextColor("#FFB74D".toColorInt())
                setStatus("● WARNING", "#FFB74D")
                safetyScore = maxOf(0f, safetyScore - 0.5f)
                
                // 🚀 Subtle Haptic for Early Fatigue
                val prefs = requireActivity().getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
                if (prefs.getBoolean("vibration", true)) {
                    val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            }
        }
        updateSafetyUI()
    }

    private fun updateSafetyUI() {
        safetyScore = safetyScore.coerceIn(0f, 100f)
        val scoreInt = safetyScore.toInt()
        tvSafetyScore.text = "$scoreInt%"
        pbSafetyScore.progress = scoreInt
        
        // 🚀 REAL-TIME SYNC: Update shared state for other fragments
        try {
            val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            prefs.edit().putInt("LAST_SCORE", scoreInt).apply()
        } catch (e: Exception) { }

        val color = when {
            safetyScore > 75 -> "#6ABF69"
            safetyScore > 45 -> "#FFB74D"
            else             -> "#EF5350"
        }
        pbSafetyScore.progressTintList = ColorStateList.valueOf(color.toColorInt())

        if (safetyScore <= 45f) {
            if (isManuallyMuted) {
                if (System.currentTimeMillis() - muteTimestamp > 10000) { 
                    isManuallyMuted = false 
                    playAudioAlarm()
                }
            } else { playAudioAlarm() }
        } else if (safetyScore > 55f) {
            isManuallyMuted = false 
            stopAudioAlarm()
        }
    }

    private fun setStatus(text: String, hexColor: String) {
        val color = hexColor.toColorInt()
        tvStatusOverlay.text = text
        tvStatusOverlay.setTextColor(color)
        statusOverlay.strokeColor = color
    }

    private fun loadAIModels() {
        try {
            val fd = requireContext().assets.openFd(lstmModelFile)
            val modelBuffer = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            tfliteInterpreter = Interpreter(modelBuffer)
        } catch (e: Exception) { Log.e(TAG, "LSTM load failed: ${e.message}") }

        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(requireContext(), options)
        } catch (e: Exception) { Log.e(TAG, "MediaPipe load failed: ${e.message}") }
    }

    private fun bindViews(view: View) {
        cameraPreview    = view.findViewById(R.id.cameraPreview)
        tvTimer          = view.findViewById(R.id.tvTimer)
        tvFPS            = view.findViewById(R.id.tvFPS)
        tvLiveEar        = view.findViewById(R.id.tvLiveEar)
        tvLiveMar        = view.findViewById(R.id.tvLiveMar)
        tvStatusOverlay  = view.findViewById(R.id.tvStatusOverlay)
        tvDetectionIcon  = view.findViewById(R.id.tvDetectionIcon)
        tvDetectionLabel = view.findViewById(R.id.tvDetectionLabel)
        tvConfidence     = view.findViewById(R.id.tvConfidence)
        tvEyeState       = view.findViewById(R.id.tvEyeState)
        tvDrowsiness     = view.findViewById(R.id.tvDrowsiness)
        tvAlertCount     = view.findViewById(R.id.tvAlertCount)
        btnStopMonitor   = view.findViewById(R.id.btnStopMonitor)
        btnMuteAlarm     = view.findViewById(R.id.btnMuteAlarm) 
        statusOverlay    = view.findViewById(R.id.statusOverlay)
        liveBadgeCard    = view.findViewById(R.id.liveBadge)
        tvLiveText       = view.findViewById(R.id.tvLiveText)
        tvSafetyScore    = view.findViewById(R.id.tvSafetyScore)
        pbSafetyScore    = view.findViewById(R.id.pbSafetyScore)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestCameraPermission() = requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
    }
}