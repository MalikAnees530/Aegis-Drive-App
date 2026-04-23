package com.malik.aegisdrive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import java.util.*

class NavigateFragment : Fragment(), SensorEventListener {

    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    
    private var currentLat = 0.0
    private var currentLng = 0.0
    private var currentHeading = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    // 🚀 TASK 1: Break the Permission Loop
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            setupLocationUpdates()
        }
    }

    private val sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "LAST_SCORE") syncPassiveSafetyStatus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_navigate, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = view.findViewById(R.id.webView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        // 🚀 TASK 1: Instant, High-Quality Map Rendering
        webView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Note: setRenderPriority is deprecated but added for legacy compatibility if needed
                @Suppress("DEPRECATION")
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }
        }

        // Initialize Sensors
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                syncPassiveSafetyStatus()
                getLastKnownLocation()
            }
        }

        webView.addJavascriptInterface(WebAppInterface(requireContext()), "Android")
        webView.loadUrl("file:///android_asset/navigate/index.html")

        checkPermissionsAndStart()
        setupInternalSpeech()

        requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    private fun checkPermissionsAndStart() {
        val fineLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLocation != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            setupLocationUpdates()
        }
    }

    private fun setupInternalSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    handler.post { webView.evaluateJavascript("window.onVoiceResult('$text');", null) }
                }
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(p0: Int) {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    private fun setupLocationUpdates() {
        // 🚀 TASK 2: High-Accuracy Continuous GPS
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateIntervalMillis(1000)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLat = location.latitude
                    currentLng = location.longitude
                    updateMapMarker()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        }
    }

    // 🚀 TASK 2.3: Bridge to JS (updateRealTimeTracking)
    private fun updateMapMarker() {
        if (!::webView.isInitialized || !isAdded) return
        handler.post { 
            webView.evaluateJavascript("javascript:updateRealTimeTracking($currentLat, $currentLng, $currentHeading);", null) 
        }
    }

    // 🚀 TASK 2.2: Device Compass (Rotation)
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            
            // Convert radians to degrees
            currentHeading = (Math.toDegrees(orientationAngles[0].toDouble()) + 360).toFloat() % 360
            updateMapMarker()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun syncPassiveSafetyStatus() {
        if (!::webView.isInitialized) return
        val score = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE).getInt("LAST_SCORE", 100)
        handler.post { webView.evaluateJavascript("window.updateSafetyScore($score);", null) }
    }

    private fun getLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let { 
                currentLat = it.latitude
                currentLng = it.longitude
                // 🚀 TASK 1: Eradicate "Blue Screen" with Instant Snap
                handler.post { 
                    webView.evaluateJavascript("javascript:initializeMapCenter(${it.latitude}, ${it.longitude});", null) 
                }
                updateMapMarker()
            }
        }
    }

    override fun onResume() { 
        super.onResume()
        syncPassiveSafetyStatus() 
        checkAndExecuteCommands()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun checkAndExecuteCommands() {
        val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
        val cmd = prefs.getString("NAV_CMD", null)
        if (cmd == "START") {
            val lat = prefs.getFloat("NAV_LAT", 0f)
            val lon = prefs.getFloat("NAV_LON", 0f)
            val name = prefs.getString("NAV_NAME", "Destination")
            
            prefs.edit().remove("NAV_CMD").apply()
            
            if (lat != 0f) {
                handler.postDelayed({
                    webView.evaluateJavascript("window.handleDestinationSelected([$lon, $lat], '$name');", null)
                    handler.postDelayed({
                        webView.evaluateJavascript("document.getElementById('btnStartDrive').click();", null)
                    }, 1500)
                }, 1000)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        try {
            webView.removeJavascriptInterface("Android")
            webView.stopLoading()
            webView.destroy()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun showToast(toast: String) { 
            handler.post { 
                val type = if (toast.contains("Success", true) || toast.contains("Saved", true)) AegisNotify.Type.SUCCESS 
                           else if (toast.contains("Failed", true) || toast.contains("Error", true)) AegisNotify.Type.ERROR
                           else AegisNotify.Type.INFO
                AegisNotify.show(mContext, toast, type) 
            } 
        }

        @JavascriptInterface
        fun startVoiceRecognition() {
            handler.post {
                val permission = Manifest.permission.RECORD_AUDIO
                if (ContextCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(arrayOf(permission))
                    return@post
                }

                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak destination...")
                    }
                    speechRecognizer?.startListening(intent)
                    AegisNotify.show(mContext, "Listening...", AegisNotify.Type.SPEECH)
                } catch (e: Exception) {
                    AegisNotify.show(mContext, "Speech recognizer failed", AegisNotify.Type.ERROR)
                }
            }
        }

        @JavascriptInterface
        fun saveHomeLocation(lat: Double, lon: Double) {
            val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putFloat("HOME_LAT", lat.toFloat())
                putFloat("HOME_LON", lon.toFloat())
                apply()
            }
        }

        @JavascriptInterface
        fun triggerNavigateHome() {
            val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            val lat = prefs.getFloat("HOME_LAT", 0f)
            val lon = prefs.getFloat("HOME_LON", 0f)
            if (lat != 0f) {
                handler.post {
                    webView.evaluateJavascript("window.handleDestinationSelected([$lon, $lat], 'Home');", null)
                }
            } else {
                handler.post { AegisNotify.show(mContext, "Please set your Home location first.", AegisNotify.Type.WARNING) }
            }
        }
    }
}
