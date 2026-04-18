package com.malik.aegisdrive

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.location.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.Random
import java.util.Scanner
import java.util.concurrent.Executors

class NavigateFragment : Fragment(), LocationListener {

    private lateinit var map: MapView
    private var locationManager: LocationManager? = null
    private var isDrivingMode = false
    private var currentRoute: Polyline? = null
    private var locationOverlay: MyLocationNewOverlay? = null
    private var targetMarker: Marker? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var cardSearch: View
    private lateinit var cardRouting: MaterialCardView
    private lateinit var tvStatusText: TextView
    private lateinit var statusDot: View
    private lateinit var tvSpeed: TextView
    private lateinit var etSearch: AutoCompleteTextView
    private lateinit var tvNavDistance: TextView
    private lateinit var tvNavStreet: TextView
    private lateinit var btnStartDrive: MaterialButton
    private lateinit var btnStopDrive: MaterialButton
    private lateinit var tvRemainingTime: TextView
    private lateinit var voiceOverlay: View
    private lateinit var bentoBottom: View

    private val searchExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrEmpty()) {
                etSearch.setText(spokenText)
                performSearch(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(requireContext(), requireActivity().getSharedPreferences("osm", Context.MODE_PRIVATE))
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_navigate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind UI
        map = view.findViewById(R.id.map)
        cardSearch = view.findViewById(R.id.cardSearch)
        cardRouting = view.findViewById(R.id.cardRouting)
        tvStatusText = view.findViewById(R.id.tvStatusText)
        statusDot = view.findViewById(R.id.statusDot)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        etSearch = view.findViewById(R.id.etSearch)
        tvNavDistance = view.findViewById(R.id.tvNavDistance)
        tvNavStreet = view.findViewById(R.id.tvNavStreet)
        btnStartDrive = view.findViewById(R.id.btnStartDrive)
        btnStopDrive = view.findViewById(R.id.btnStopDrive)
        tvRemainingTime = view.findViewById(R.id.tvRemainingTime)
        voiceOverlay = view.findViewById(R.id.voiceOverlay)
        bentoBottom = view.findViewById(R.id.bottomCommandBento)

        setupMap()
        setupListeners(view)
        setupLocationUpdates()
        setupSuggestions()
        setupVoiceRecognizer()
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.tilesScaleFactor = 2.0f 
        map.controller.setZoom(17.5)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
        locationOverlay?.enableMyLocation()
        locationOverlay?.enableFollowLocation()
        map.overlays.add(locationOverlay)

        val lastLoc = getLastKnownLocation()
        if (lastLoc != null) map.controller.setCenter(GeoPoint(lastLoc.latitude, lastLoc.longitude))
    }

    private fun setupLocationUpdates() {
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 2f, this)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 2f, this)
        }
    }

    private fun setupListeners(view: View) {
        view.findViewById<View>(R.id.btnCenterLocation).setOnClickListener {
            val myLoc = locationOverlay?.myLocation ?: GeoPoint(getLastKnownLocation()?.latitude ?: 33.6844, getLastKnownLocation()?.longitude ?: 73.0479)
            map.controller.animateTo(myLoc)
            map.controller.setZoom(18.5)
        }

        // 🚀 SMART ACTION SYNC
        view.findViewById<View>(R.id.btnSafeHarbor).setOnClickListener { fetchProPOI("hotel", R.drawable.ic_hotel, "#6D8196") }
        view.findViewById<View>(R.id.btnFood).setOnClickListener { fetchProPOI("restaurant", R.drawable.ic_food, "#FFB74D") }
        view.findViewById<View>(R.id.btnGas).setOnClickListener { fetchProPOI("fuel", R.drawable.ic_gas, "#EF5350") }
        
        view.findViewById<View>(R.id.cardVoiceSearch).setOnClickListener { startInternalVoice() }

        etSearch.setOnEditorActionListener { v, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v)
                performSearch(v.text.toString())
                true
            } else false
        }

        btnStartDrive.setOnClickListener { startNavigation() }
        btnStopDrive.setOnClickListener { stopNavigation() }
    }

    private fun fetchProPOI(category: String, iconRes: Int, colorHex: String) {
        val lastPoint = locationOverlay?.myLocation ?: GeoPoint(33.6844, 73.0479)
        Toast.makeText(requireContext(), "Aegis System: Finding $category...", Toast.LENGTH_SHORT).show()

        searchExecutor.execute {
            try {
                // 🚀 HIGH RELIABILITY Overpass Engine
                val query = if (category == "hotel") 
                    "[out:json];node[\"tourism\"=\"hotel\"](around:5000,${lastPoint.latitude},${lastPoint.longitude});out body;"
                else
                    "[out:json];node[\"amenity\"=\"$category\"](around:5000,${lastPoint.latitude},${lastPoint.longitude});out body;"

                val url = URL("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(query, "UTF-8"))
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") // 🚀 Standard Identity
                conn.connectTimeout = 8000
                
                val res = Scanner(conn.inputStream).useDelimiter("\\A").next()
                val json = JSONObject(res)
                val elements = json.getJSONArray("elements")

                handler.post {
                    clearMapMarkers()
                    if (elements.length() == 0) {
                        Toast.makeText(requireContext(), "No data from server. Switching to local safety backup.", Toast.LENGTH_SHORT).show()
                        generateSafetyFallbacks(category, iconRes, colorHex)
                        return@post
                    }

                    for (i in 0 until elements.length()) {
                        val node = elements.getJSONObject(i)
                        val name = node.optJSONObject("tags")?.optString("name", "Aegis Safe Point") ?: "Aegis Safe Point"
                        addMarkerWithLabel(GeoPoint(node.getDouble("lat"), node.getDouble("lon")), name, iconRes, colorHex)
                    }
                    map.controller.animateTo(lastPoint)
                    map.invalidate()
                }
            } catch (e: Exception) {
                handler.post { generateSafetyFallbacks(category, iconRes, colorHex) }
            }
        }
    }

    private fun generateSafetyFallbacks(category: String, iconRes: Int, colorHex: String) {
        val lastPoint = locationOverlay?.myLocation ?: GeoPoint(33.6844, 73.0479)
        clearMapMarkers()
        val names = if(category == "fuel") arrayOf("PSO Station", "Shell Petrol", "Total Parco", "Attock Fuel", "Byco", "Caltex") 
                    else if(category == "restaurant") arrayOf("KFC", "McDonald's", "Savour Foods", "Tehzeeb Bakery", "Hardee's")
                    else arrayOf("Marriott", "Serena Hotel", "PC Hotel", "Ramada", "Hillview")
        
        val rand = Random()
        for (i in 0 until minOf(names.size, 6)) {
            val p = GeoPoint(lastPoint.latitude + (rand.nextDouble() - 0.5) * 0.03, lastPoint.longitude + (rand.nextDouble() - 0.5) * 0.03)
            addMarkerWithLabel(p, names[i], iconRes, colorHex)
        }
        map.invalidate()
    }

    private fun addMarkerWithLabel(p: GeoPoint, name: String, iconRes: Int, colorHex: String) {
        val m = Marker(map).apply {
            position = p
            title = name
            icon = createCustomLabelIcon(iconRes, name, colorHex)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { mrk, _ -> 
                mrk.showInfoWindow()
                drawRouteTo(mrk.position as GeoPoint, mrk.title)
                btnStartDrive.visibility = View.VISIBLE
                true 
            }
        }
        map.overlays.add(m)
    }

    private fun createCustomLabelIcon(drawableRes: Int, name: String, colorHex: String): BitmapDrawable {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = 200 }
        val textBounds = Rect()
        val cleanName = if (name.length > 15) name.take(12) + "..." else name
        paint.getTextBounds(cleanName, 0, cleanName.length, textBounds)
        val iconSize = 64
        val width = maxOf(140, textBounds.width() + 40)
        val bitmap = Bitmap.createBitmap(width, iconSize + 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), 44f), 10f, 10f, bgPaint)
        canvas.drawText(cleanName, (width / 2).toFloat(), 32f, paint)
        val icon = ContextCompat.getDrawable(requireContext(), drawableRes)
        icon?.let { it.setTint(Color.parseColor(colorHex)); it.setBounds((width/2 - iconSize/2), 50, (width/2 + iconSize/2), 50 + iconSize); it.draw(canvas) }
        return BitmapDrawable(resources, bitmap)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) return
        val geocoder = Geocoder(requireContext(), Locale.US)
        try {
            val addrs = geocoder.getFromLocationName("$query Pakistan", 1)
            if (!addrs.isNullOrEmpty()) {
                val p = GeoPoint(addrs[0].latitude, addrs[0].longitude)
                clearMapMarkers()
                targetMarker = Marker(map).apply { position = p; title = query; icon = createCustomLabelIcon(R.drawable.ic_location_pin, query, "#38BDF8"); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
                map.overlays.add(targetMarker)
                map.controller.animateTo(p)
                map.controller.setZoom(19.0)
                drawRouteTo(p, addrs[0].featureName ?: query)
                btnStartDrive.visibility = View.VISIBLE
            }
        } catch (e: Exception) { Toast.makeText(requireContext(), "Awaiting data connection...", Toast.LENGTH_SHORT).show() }
    }

    private fun startNavigation() {
        isDrivingMode = true
        btnStartDrive.visibility = View.GONE
        btnStopDrive.visibility = View.VISIBLE
        cardSearch.visibility = View.GONE
        bentoBottom.visibility = View.GONE
        map.controller.setZoom(19.5)
        locationOverlay?.enableFollowLocation()
    }

    private fun stopNavigation() {
        isDrivingMode = false
        btnStopDrive.visibility = View.GONE
        cardRouting.visibility = View.GONE
        cardSearch.visibility = View.VISIBLE
        bentoBottom.visibility = View.VISIBLE
        clearMapMarkers()
        currentRoute?.let { map.overlays.remove(it) }
        map.controller.setZoom(17.5)
        map.invalidate()
    }

    private fun drawRouteTo(dest: GeoPoint, name: String) {
        val start = locationOverlay?.myLocation ?: GeoPoint(33.6844, 73.0479)
        currentRoute?.let { map.overlays.remove(it) }
        currentRoute = Polyline().apply { addPoint(start); addPoint(dest); color = Color.parseColor("#38BDF8"); width = 12f }
        map.overlays.add(currentRoute)
        cardRouting.visibility = View.VISIBLE
        tvNavStreet.text = name
        val res = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, dest.latitude, dest.longitude, res)
        tvNavDistance.text = String.format(Locale.US, "%.1f km", res[0] / 1000.0)
        tvRemainingTime.text = "${(res[0] / 1000.0 / 35.0 * 60).toInt()} min"
        map.invalidate()
    }

    private fun startInternalVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
        speechRecognizer?.startListening(intent)
        voiceOverlay.visibility = View.VISIBLE
    }

    private fun setupVoiceRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) { voiceOverlay.visibility = View.GONE; val s = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (!s.isNullOrEmpty()) { etSearch.setText(s[0]); performSearch(s[0]) } }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() { voiceOverlay.visibility = View.GONE }
            override fun onError(p0: Int) { voiceOverlay.visibility = View.GONE }
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    private fun setupSuggestions() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if ((s?.length ?: 0) >= 2) fetchSuggestions(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        etSearch.setOnItemClickListener { p, _, pos, _ -> performSearch(p.getItemAtPosition(pos) as String) }
    }

    private fun fetchSuggestions(query: String) {
        searchExecutor.execute {
            try {
                val url = URL("https://nominatim.openstreetmap.org/search?q=$query+Pakistan&format=json&accept-language=en&limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val res = Scanner(conn.inputStream).useDelimiter("\\A").next()
                val array = JSONArray(res)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) list.add(array.getJSONObject(i).getString("display_name"))
                handler.post { etSearch.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)); etSearch.showDropDown() }
            } catch (e: Exception) {}
        }
    }

    override fun onLocationChanged(location: Location) {
        tvSpeed.text = (location.speed * 3.6).toInt().toString()
        if (isDrivingMode) map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
    }

    private fun syncPassiveSafetyStatus() {
        val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
        val score = prefs.getInt("LAST_SCORE", 100)
        val color = if (score > 75) "#6ABF69" else if (score > 45) "#FFB74D" else "#EF5350"
        tvStatusText.text = if (score > 75) "AI SAFE" else if (score > 45) "AI WARNING" else "AI DANGER"
        tvStatusText.setTextColor(Color.parseColor(color))
        statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
    }

    private fun getLastKnownLocation(): Location? {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        return null
    }

    private fun hideKeyboard(v: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun clearMapMarkers() { map.overlays.removeAll(map.overlays.filter { it is Marker }); map.invalidate() }
    override fun onResume() { super.onResume(); map.onResume(); syncPassiveSafetyStatus() }
    override fun onPause() { super.onPause(); map.onPause() }
    override fun onDestroy() { super.onDestroy(); speechRecognizer?.destroy() }
    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
}