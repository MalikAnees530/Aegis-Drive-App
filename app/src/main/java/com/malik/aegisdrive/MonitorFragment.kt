package com.malik.aegisdrive

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MonitorFragment : Fragment() {

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var cameraPreview: PreviewView
    private lateinit var tvTimer: TextView
    private lateinit var tvFPS: TextView
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvDetectionIcon: TextView
    private lateinit var tvDetectionLabel: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvEyeState: TextView
    private lateinit var tvDrowsiness: TextView
    private lateinit var tvAlertCount: TextView
    private lateinit var btnStopMonitor: MaterialButton
    private lateinit var statusOverlay: MaterialCardView
    private lateinit var liveBadgeCard: MaterialCardView
    private lateinit var tvLiveText: TextView
    private lateinit var tvSafetyScore: TextView
    private lateinit var pbSafetyScore: ProgressBar

    // ── Camera & AI ────────────────────────────────────────────────────────
    private var cameraExecutor: ExecutorService? = null
    private var tfliteInterpreter: Interpreter? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // 🚀 STRICT STATE FLAG: AI only analyzes when this is true
    private var isMonitoring = false

    // ── Safety Engine ──────────────────────────────────────────────────────
    private var safetyScore = 100f
    private var currentRingtone: Ringtone? = null
    private var alertCount = 0

    // Consecutive frame counters
    private var consecutiveDangerFrames = 0
    private var consecutiveWarnFrames = 0
    private val DANGER_THRESHOLD = 5
    private val WARN_THRESHOLD   = 4

    // ── Model ──────────────────────────────────────────────────────────────
    private val MODEL_INPUT_SIZE = 224
    private val NUM_CLASSES = 3
    private val LABELS = arrayOf("Eyes Closed", "Normal", "Yawning")
    private val MODEL_FILE = "aegis_drive_model.tflite"

    // ── FPS Tracking ───────────────────────────────────────────────────────
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    // ── Timer ──────────────────────────────────────────────────────────────
    private val timerHandler = Handler(Looper.getMainLooper())
    private var elapsedSeconds = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            elapsedSeconds++
            val h = elapsedSeconds / 3600
            val m = (elapsedSeconds % 3600) / 60
            val s = elapsedSeconds % 60
            tvTimer.text = String.format("%02d:%02d:%02d", h, m, s)
            timerHandler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val TAG = "AegisDrive"
        private const val CAMERA_PERMISSION_CODE = 200
    }

    // ══════════════════════════════════════════════════════════════════════
    // LIFECYCLE (FIXED AUTO-START BUG)
    // ══════════════════════════════════════════════════════════════════════

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_monitor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        loadTFLiteModel()

        // 🚀 BUG FIX: Explicitly enforce IDLE state on creation.
        isMonitoring = false
        setUIMonitoringState(false)

        // Only start the visual camera stream so the user can frame themselves
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        btnStopMonitor.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission()
                return@setOnClickListener
            }
            if (isMonitoring) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure that if they tab away and come back, it doesn't automatically start analyzing
        if (!isMonitoring) {
            setUIMonitoringState(false)
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop monitoring immediately if the app goes to the background
        stopMonitoring()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopMonitoring()
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        tfliteInterpreter?.close()
    }

    // ══════════════════════════════════════════════════════════════════════
    // MONITORING CONTROL
    // ══════════════════════════════════════════════════════════════════════

    private fun startMonitoring() {
        isMonitoring = true
        elapsedSeconds = 0
        alertCount = 0
        safetyScore = 100f
        consecutiveDangerFrames = 0
        consecutiveWarnFrames = 0
        frameCount = 0
        lastFpsTime = System.currentTimeMillis()

        tvAlertCount.text = "0"
        tvTimer.text = "00:00:00"

        updateSafetyUI()
        setUIMonitoringState(true)

        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.post(timerRunnable)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        timerHandler.removeCallbacks(timerRunnable)
        currentRingtone?.stop()
        setUIMonitoringState(false)
    }

    // ══════════════════════════════════════════════════════════════════════
    // UI STATE & LIVE BADGE SYNC
    // ══════════════════════════════════════════════════════════════════════

    private fun setUIMonitoringState(isActive: Boolean) {
        if (isActive) {
            // ── ACTIVE: Green Badge, Red Stop Button ──────────────────────
            btnStopMonitor.text = "STOP MONITORING"
            btnStopMonitor.setTextColor(Color.parseColor("#EF5350"))
            btnStopMonitor.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CC0D1B26"))
            btnStopMonitor.strokeWidth = 2
            btnStopMonitor.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF5350"))

            // 🚀 LIVE BADGE SYNC (GREEN)
            liveBadgeCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#AA22C55E")))
            tvLiveText.text = "LIVE"

            setStatus("● SAFE", "#6ABF69")
        } else {
            // ── IDLE: Red Badge, Blue Begin Button ────────────────────────
            btnStopMonitor.text = "BEGIN MONITORING"
            btnStopMonitor.setTextColor(Color.parseColor("#0F172A"))
            btnStopMonitor.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            btnStopMonitor.strokeWidth = 0

            // 🚀 LIVE BADGE SYNC (RED)
            liveBadgeCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#AAEF4444")))
            tvLiveText.text = "OFFLINE"

            // Reset UI
            setStatus("● STANDBY", "#6D8196")
            tvDetectionIcon.text = "–"
            tvDetectionLabel.text = "Waiting..."
            tvDetectionLabel.setTextColor(Color.parseColor("#6D8196"))
            tvConfidence.text = "–"
            tvConfidence.setTextColor(Color.parseColor("#6D8196"))
            tvEyeState.text = "–"
            tvEyeState.setTextColor(Color.parseColor("#6D8196"))
            tvDrowsiness.text = "–"
            tvDrowsiness.setTextColor(Color.parseColor("#6D8196"))
            tvFPS.text = "0 FPS"
            tvTimer.text = "00:00:00"
            safetyScore = 100f
            updateSafetyUI()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CAMERA — High-Speed Dual Stream Architecture
    // ══════════════════════════════════════════════════════════════════════

    private fun startCamera() {
        if (cameraExecutor == null || cameraExecutor!!.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 1. HD Preview for the screen
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1080, 1920))
                    .build()
                    .also { it.setSurfaceProvider(cameraPreview.surfaceProvider) }

                // 2. Optimized Analyzer for AI (Prevents FPS Lag)
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            analyzeFrame(imageProxy)
                        }
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )

            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ══════════════════════════════════════════════════════════════════════
    // AI ANALYSIS (FIXED FALSE YAWN LOGIC)
    // ══════════════════════════════════════════════════════════════════════

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

            val bitmap = imageProxy.toBitmap()
            if (tfliteInterpreter != null) {
                runTFLiteInference(bitmap)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Frame error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun runTFLiteInference(bitmap: Bitmap) {
        val interpreter = tfliteInterpreter ?: return

        // 1. 🚀 THE "DASHBOARD ANGLE" CROP
        // We push the crop slightly down by 10% so the mouth is always inside the AI's vision.
        val minDim = minOf(bitmap.width, bitmap.height)
        val startX = (bitmap.width - minDim) / 2
        val startY = ((bitmap.height - minDim) * 0.10).toInt().coerceAtLeast(0)

        val squareBitmap = Bitmap.createBitmap(bitmap, startX, startY, minDim, minDim)

        // Mirror for front camera and resize to 224
        val matrix = Matrix().apply { postScale(-1f, 1f, minDim / 2f, minDim / 2f) }
        val flipped = Bitmap.createBitmap(squareBitmap, 0, 0, minDim, minDim, matrix, true)
        val resized  = Bitmap.createScaledBitmap(flipped, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)

        val inputBuffer = ByteBuffer
            .allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3)
            .apply { order(ByteOrder.nativeOrder()) }

        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        resized.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        // 0.0 to 1.0 Normalization
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            inputBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255.0f)
            inputBuffer.putFloat((pixel          and 0xFF) / 255.0f)
        }

        try {
            val output = Array(1) { FloatArray(NUM_CLASSES) }
            interpreter.run(inputBuffer, output)
            val scores = output[0]

            // 2. 🚀 TRUSTING THE MODEL (FALSE YAWN FIX)
            // Removed arbitrary thresholds that caused "Eyes Closed" to be falsely labeled as "Yawning".
            // We now strictly trust the AI's Softmax maximum confidence.
            val rawIndex = scores.indices.maxByOrNull { scores[it] } ?: 1
            val confidence = (scores[rawIndex] * 100).toInt().coerceIn(0, 100)

            // 3. 🚀 ANTI-SPAM ALARM TRIGGER (State-Transition Based)
            when (rawIndex) {
                0 -> { // Eyes Closed detected
                    consecutiveDangerFrames++
                    consecutiveWarnFrames = 0

                    // Trigger Alarm EXACTLY ONCE when the Danger Threshold is crossed.
                    if (consecutiveDangerFrames == DANGER_THRESHOLD) {
                        activity?.runOnUiThread { updateUI(0, confidence, playAlert = true) }
                    } else if (consecutiveDangerFrames > DANGER_THRESHOLD) {
                        activity?.runOnUiThread { updateUI(0, confidence, playAlert = false) }
                    }
                }
                2 -> { // Yawning detected
                    consecutiveWarnFrames++
                    consecutiveDangerFrames = 0

                    // Trigger Warning EXACTLY ONCE when Threshold is crossed.
                    if (consecutiveWarnFrames == WARN_THRESHOLD) {
                        activity?.runOnUiThread { updateUI(2, confidence, playAlert = true) }
                    } else if (consecutiveWarnFrames > WARN_THRESHOLD) {
                        activity?.runOnUiThread { updateUI(2, confidence, playAlert = false) }
                    }
                }
                else -> { // Normal detected
                    consecutiveDangerFrames = 0
                    consecutiveWarnFrames = 0
                    activity?.runOnUiThread { updateUI(1, confidence, playAlert = false) }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UI & ANTI-SPAM SAFETY SCORE
    // ══════════════════════════════════════════════════════════════════════

    private fun updateUI(labelIndex: Int, confidence: Int, playAlert: Boolean) {
        if (!isMonitoring) return

        tvDetectionLabel.text = LABELS[labelIndex]
        tvConfidence.text = "$confidence%"

        when (labelIndex) {
            0 -> { // EYES CLOSED
                tvDetectionIcon.text = "😴"
                tvDetectionLabel.setTextColor(Color.parseColor("#EF5350"))
                tvConfidence.setTextColor(Color.parseColor("#EF5350"))
                tvEyeState.text = "Closed"
                tvEyeState.setTextColor(Color.parseColor("#EF5350"))
                tvDrowsiness.text = "Drowsy"
                tvDrowsiness.setTextColor(Color.parseColor("#EF5350"))
                setStatus("● DANGER", "#EF5350")

                safetyScore = maxOf(0f, safetyScore - 1.5f)
                if (playAlert) triggerAlert(urgent = true)
            }
            1 -> { // NORMAL
                tvDetectionIcon.text = "👁"
                tvDetectionLabel.setTextColor(Color.parseColor("#6ABF69"))
                tvConfidence.setTextColor(Color.parseColor("#6ABF69"))
                tvEyeState.text = "Open"
                tvEyeState.setTextColor(Color.parseColor("#6ABF69"))
                tvDrowsiness.text = "None"
                tvDrowsiness.setTextColor(Color.parseColor("#6ABF69"))
                setStatus("● SAFE", "#6ABF69")

                safetyScore = minOf(100f, safetyScore + 1.0f)
            }
            2 -> { // YAWNING
                tvDetectionIcon.text = "🥱"
                tvDetectionLabel.setTextColor(Color.parseColor("#FFB74D"))
                tvConfidence.setTextColor(Color.parseColor("#FFB74D"))
                tvEyeState.text = "Open"
                tvEyeState.setTextColor(Color.parseColor("#6ABF69"))
                tvDrowsiness.text = "Yawning"
                tvDrowsiness.setTextColor(Color.parseColor("#FFB74D"))
                setStatus("● WARNING", "#FFB74D")

                safetyScore = maxOf(0f, safetyScore - 0.5f)
                if (playAlert) triggerAlert(urgent = false)
            }
        }

        updateSafetyUI()
    }

    private fun updateSafetyUI() {
        val scoreInt = safetyScore.toInt()
        tvSafetyScore.text = "$scoreInt%"
        pbSafetyScore.progress = scoreInt

        val color = when {
            safetyScore > 75 -> "#6ABF69"
            safetyScore > 50 -> "#FFB74D"
            else             -> "#EF5350"
        }
        pbSafetyScore.progressTintList = ColorStateList.valueOf(Color.parseColor(color))
    }

    // 🚀 ALARM SPAM FIX: The alarm now only triggers precisely when a new mistake occurs.
    private fun triggerAlert(urgent: Boolean) {
        alertCount++
        tvAlertCount.text = alertCount.toString()

        try {
            if (currentRingtone == null || !currentRingtone!!.isPlaying) {
                val type = if (urgent) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                val alertUri = RingtoneManager.getDefaultUri(type)
                currentRingtone = RingtoneManager.getRingtone(requireContext(), alertUri)
                currentRingtone?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Alert error: ${e.message}")
        }
    }

    private fun setStatus(text: String, hexColor: String) {
        val color = Color.parseColor(hexColor)
        tvStatusOverlay.text = text
        tvStatusOverlay.setTextColor(color)
        statusOverlay.strokeColor = color
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun loadTFLiteModel() {
        try {
            val fd = requireContext().assets.openFd(MODEL_FILE)
            val modelBuffer: MappedByteBuffer = FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
            )
            tfliteInterpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed: ${e.message}")
        }
    }

    private fun bindViews(view: View) {
        cameraPreview    = view.findViewById(R.id.cameraPreview)
        tvTimer          = view.findViewById(R.id.tvTimer)
        tvFPS            = view.findViewById(R.id.tvFPS)
        tvStatusOverlay  = view.findViewById(R.id.tvStatusOverlay)
        tvDetectionIcon  = view.findViewById(R.id.tvDetectionIcon)
        tvDetectionLabel = view.findViewById(R.id.tvDetectionLabel)
        tvConfidence     = view.findViewById(R.id.tvConfidence)
        tvEyeState       = view.findViewById(R.id.tvEyeState)
        tvDrowsiness     = view.findViewById(R.id.tvDrowsiness)
        tvAlertCount     = view.findViewById(R.id.tvAlertCount)
        btnStopMonitor   = view.findViewById(R.id.btnStopMonitor)
        statusOverlay    = view.findViewById(R.id.statusOverlay)
        liveBadgeCard    = view.findViewById(R.id.liveBadge)
        tvLiveText       = view.findViewById(R.id.tvLiveText)
        tvSafetyScore    = view.findViewById(R.id.tvSafetyScore)
        pbSafetyScore    = view.findViewById(R.id.pbSafetyScore)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestCameraPermission() = ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }
}