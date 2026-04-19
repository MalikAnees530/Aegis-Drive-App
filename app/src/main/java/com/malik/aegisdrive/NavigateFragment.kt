package com.malik.aegisdrive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.util.*

class NavigateFragment : Fragment(), LocationListener {

    private lateinit var webView: WebView
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

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
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                syncPassiveSafetyStatus()
                getLastKnownLocation()?.let { onLocationChanged(it) }
            }
        }

        webView.addJavascriptInterface(WebAppInterface(requireContext()), "Android")
        webView.loadUrl("file:///android_asset/navigate/index.html")

        setupLocationUpdates()
        setupInternalSpeech()

        requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(sharedPrefsListener)
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
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 2f, this) } catch (e: Exception) {}
            try { locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 2f, this) } catch (e: Exception) {}
        }
    }

    private fun syncPassiveSafetyStatus() {
        if (!::webView.isInitialized) return
        val score = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE).getInt("LAST_SCORE", 100)
        handler.post { webView.evaluateJavascript("window.updateSafetyScore($score);", null) }
    }

    override fun onLocationChanged(location: Location) {
        if (!::webView.isInitialized || !isAdded) return
        
        // 🚀 SENIOR FIX: Advanced Speed & Bearing Filtering
        // GPS speed can fluctuate. Use hasSpeed() and ignore tiny drifts under 1.5 km/h.
        var speedKmh = 0.0
        var heading = -1f // -1 means keep current map rotation

        if (location.hasSpeed()) {
            speedKmh = location.speed * 3.6
            if (speedKmh < 1.5) speedKmh = 0.0 // Eliminate jitter when stopped
        }

        // Only update camera bearing if we are actually moving to prevent wild map spinning at traffic lights
        if (location.hasBearing() && speedKmh > 1.5) {
            heading = location.bearing
        }

        handler.post { 
            webView.evaluateJavascript("window.updateLocation(${location.latitude}, ${location.longitude}, $speedKmh, $heading);", null) 
        }
    }

    private fun getLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try { locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
    }

    override fun onResume() { super.onResume(); syncPassiveSafetyStatus() }
    
    // 🚀 SENIOR FIX: Strict Memory Leak Prevention for WebView
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            webView.removeJavascriptInterface("Android")
            webView.stopLoading()
            webView.destroy()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        speechRecognizer?.destroy()
        requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun showToast(toast: String) { handler.post { Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show() } }

        @JavascriptInterface
        fun startVoiceRecognition() {
            handler.post {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
                speechRecognizer?.startListening(intent)
            }
        }
    }
}
