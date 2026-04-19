package com.malik.aegisdrive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

data class ChatSession(val id: String, var name: String, val messages: MutableList<Pair<String, String>>)

class ChatFragment : Fragment() {

    private val GROQ_API_KEY = "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"
    private val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var sessionsContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var etChatInput: EditText
    private lateinit var tvSafetyMirror: TextView
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var ttsReady = false
    private var isLiveMode = false

    private var currentSession: ChatSession = ChatSession(UUID.randomUUID().toString(), "New Chat", mutableListOf())
    private var allSessions = mutableListOf<ChatSession>()

    private val sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "LAST_SCORE") activity?.runOnUiThread { syncSafetyStatus() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        loadSessions()
        initTTS()
        syncSafetyStatus()
        
        requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(sharedPrefsListener)

        if (currentSession.messages.isEmpty()) {
            addAiMessage("Hello Driver! I am Aegis AI. I can check your safety score or navigate you home. How can I assist you?")
        } else {
            renderCurrentSession()
        }

        view.findViewById<MaterialCardView>(R.id.btnSend).setOnClickListener { handleSend() }
        view.findViewById<MaterialCardView>(R.id.btnMic).setOnClickListener { handleMicClick() }
        view.findViewById<MaterialCardView>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        view.findViewById<MaterialCardView>(R.id.btnNewChat).setOnClickListener { startNewChat() }
        view.findViewById<View>(R.id.btnDeleteAllSessions).setOnClickListener { deleteAllSessions() }

        setupChips(view)
        renderSessionList()
    }

    private fun bindViews(view: View) {
        drawerLayout = view.findViewById(R.id.drawerLayout)
        chatMessagesContainer = view.findViewById(R.id.chatMessagesContainer)
        sessionsContainer = view.findViewById(R.id.sessionsContainer)
        chatScrollView = view.findViewById(R.id.chatScrollView)
        etChatInput = view.findViewById(R.id.etChatInput)
        tvSafetyMirror = view.findViewById(R.id.tvSafetyMirror)
    }

    private fun setupChips(view: View) {
        view.findViewById<View>(R.id.chipSafety).setOnClickListener { sendMessage("My current safety score?") }
        view.findViewById<View>(R.id.chipHome).setOnClickListener { navigateHome() }
        
        // Hide hospital chip if it exists in the layout but we want it gone
        view.findViewById<View>(R.id.chipHospital)?.visibility = View.GONE
    }

    private fun syncSafetyStatus() {
        val prefs = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE) ?: return
        val score = prefs.getInt("LAST_SCORE", 100)
        val status = if (score > 75) "AI SAFE" else if (score > 45) "AI WARNING" else "AI DANGER"
        val color = if (score > 75) "#22C55E" else if (score > 45) "#F59E0B" else "#EF4444"
        
        tvSafetyMirror.text = "$status • $score%"
        try {
            tvSafetyMirror.setTextColor(android.graphics.Color.parseColor(color))
        } catch (e: Exception) {}
    }

    private fun handleSend() {
        val text = etChatInput.text.toString().trim()
        if (text.isNotEmpty()) {
            sendMessage(text)
            etChatInput.setText("")
        }
    }

    private fun sendMessage(text: String) {
        if (currentSession.messages.isEmpty()) {
            currentSession.name = if (text.length > 20) text.substring(0, 18) + "..." else text
            if (!allSessions.contains(currentSession)) allSessions.add(0, currentSession)
            renderSessionList()
        }
        
        addUserMessage(text)
        val typing = addTypingIndicator()
        currentSession.messages.add(Pair("user", text))
        
        CoroutineScope(Dispatchers.IO).launch {
            val response = callGroqAPI(text)
            withContext(Dispatchers.Main) {
                chatMessagesContainer.removeView(typing)
                addAiMessage(response)
                currentSession.messages.add(Pair("assistant", response))
                saveSessions()
                if (isLiveMode) speak(response)
            }
        }
    }

    private fun startNewChat() {
        currentSession = ChatSession(UUID.randomUUID().toString(), "New Chat", mutableListOf())
        chatMessagesContainer.removeAllViews()
        addAiMessage("New session started. How can I assist you?")
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun renderCurrentSession() {
        chatMessagesContainer.removeAllViews()
        for (msg in currentSession.messages) {
            if (msg.first == "user") addUserMessage(msg.second, false)
            else addAiMessage(msg.second, false)
        }
        scrollToBottom()
    }

    private fun renderSessionList() {
        sessionsContainer.removeAllViews()
        for (session in allSessions) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = 0f
                strokeColor = resources.getColor(R.color.border_subtle, null)
                strokeWidth = dpToPx(1)
                setCardBackgroundColor(if (session == currentSession) resources.getColor(R.color.bg_elevated, null) else android.graphics.Color.TRANSPARENT)
            }

            val tv = TextView(requireContext()).apply {
                text = session.name
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
                textSize = 14f
                typeface = if (session == currentSession) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            
            card.addView(tv)
            card.setOnClickListener {
                currentSession = session
                renderCurrentSession()
                renderSessionList()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            
            card.setOnLongClickListener {
                deleteSession(session)
                true
            }
            
            sessionsContainer.addView(card)
        }
    }

    private fun deleteSession(session: ChatSession) {
        allSessions.remove(session)
        if (currentSession == session) startNewChat()
        saveSessions()
        renderSessionList()
        Toast.makeText(requireContext(), "Session deleted", Toast.LENGTH_SHORT).show()
    }

    private fun deleteAllSessions() {
        allSessions.clear()
        startNewChat()
        saveSessions()
        renderSessionList()
        Toast.makeText(requireContext(), "All history cleared", Toast.LENGTH_SHORT).show()
    }

    private fun saveSessions() {
        val prefs = requireActivity().getSharedPreferences("AegisChat", Context.MODE_PRIVATE)
        val array = JSONArray()
        for (session in allSessions) {
            val obj = JSONObject().apply {
                put("id", session.id)
                put("name", session.name)
                val msgs = JSONArray()
                for (m in session.messages) {
                    msgs.put(JSONObject().apply { put("r", m.first); put("c", m.second) })
                }
                put("msgs", msgs)
            }
            array.put(obj)
        }
        prefs.edit().putString("SESSIONS_JSON", array.toString()).apply()
    }

    private fun loadSessions() {
        val prefs = requireActivity().getSharedPreferences("AegisChat", Context.MODE_PRIVATE)
        val json = prefs.getString("SESSIONS_JSON", null) ?: return
        try {
            val array = JSONArray(json)
            allSessions.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val msgs = mutableListOf<Pair<String, String>>()
                val mArray = obj.getJSONArray("msgs")
                for (j in 0 until mArray.length()) {
                    val mObj = mArray.getJSONObject(j)
                    msgs.add(Pair(mObj.getString("r"), mObj.getString("c")))
                }
                allSessions.add(ChatSession(id, name, msgs))
            }
            if (allSessions.isNotEmpty()) currentSession = allSessions[0]
        } catch (e: Exception) {}
    }

    private fun navigateHome() {
        val prefs = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE) ?: return
        val homeLat = prefs.getFloat("HOME_LAT", 0f)
        val homeLon = prefs.getFloat("HOME_LON", 0f)

        if (homeLat == 0f) {
            addAiMessage("You haven't set a Home location yet. Please go to the Navigate tab, click Home 🏠, then 'Edit Home' to set it.")
        } else {
            addAiMessage("Redirecting to Map and starting your drive home...")
            startNavigation(homeLat.toDouble(), homeLon.toDouble(), "Home")
        }
    }

    private fun startNavigation(lat: Double, lon: Double, name: String) {
        val prefs = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE) ?: return
        prefs.edit().apply {
            putString("NAV_CMD", "START")
            putFloat("NAV_LAT", lat.toFloat())
            putFloat("NAV_LON", lon.toFloat())
            putString("NAV_NAME", name)
            apply()
        }
        val nav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        nav?.selectedItemId = R.id.navigateFragment
    }

    private fun callGroqAPI(userMessage: String): String {
        return try {
            val conn = URL(GROQ_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val prefs = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            val score = prefs?.getInt("LAST_SCORE", 100) ?: 100

            val systemPrompt = "You are Aegis AI, a safety assistant. Driver safety score is $score%."

            val body = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    val history = currentSession.messages.takeLast(6)
                    for (m in history) {
                        put(JSONObject().apply { put("role", if (m.first == "user") "user" else "assistant"); put("content", m.second) })
                    }
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                })
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString()); writer.flush(); writer.close()

            if (conn.responseCode == 200) {
                val raw = conn.inputStream.bufferedReader().readText()
                JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else "Aegis AI is momentarily busy. Please try again."
        } catch (e: Exception) { "Connection unstable." }
    }

    private fun addUserMessage(text: String, scroll: Boolean = true) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dpToPx(12)) }
        }

        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.accent_primary, null))
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            textSize = 14f
            maxWidth = dpToPx(260)
        }
        card.addView(tv)
        container.addView(card)
        chatMessagesContainer.addView(container)
        if (scroll) scrollToBottom()
    }

    private fun addAiMessage(text: String, scroll: Boolean = true) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dpToPx(12)) }
        }

        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.bg_surface, null))
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            strokeColor = resources.getColor(R.color.border_subtle, null)
            strokeWidth = dpToPx(1)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tv = TextView(requireContext()).apply {
            this.text = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            textSize = 14f
            maxWidth = dpToPx(260)
            movementMethod = LinkMovementMethod.getInstance()
        }
        card.addView(tv)
        container.addView(card)
        chatMessagesContainer.addView(container)
        if (scroll) scrollToBottom()
    }

    private fun addTypingIndicator(): View {
        val tv = TextView(requireContext()).apply { 
            text = "Aegis is thinking..."
            setTextColor(resources.getColor(R.color.text_disabled, null))
            setPadding(0, 0, 0, dpToPx(12))
            textSize = 12f
        }
        chatMessagesContainer.addView(tv)
        scrollToBottom()
        return tv
    }

    private fun handleMicClick() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (requireContext().checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 101); return
        }
        if (isListening) stopListening() else startListening()
    }

    private fun startListening() {
        try {
            isListening = true; isLiveMode = true
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val matches = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) sendMessage(matches[0])
                    stopListening()
                }
                override fun onReadyForSpeech(p0: Bundle?) { Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() { stopListening() }
                override fun onError(p0: Int) { stopListening(); Toast.makeText(context, "Try speaking again.", Toast.LENGTH_SHORT).show() }
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) { stopListening() }
    }

    private fun stopListening() { 
        isListening = false
        activity?.runOnUiThread {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun initTTS() { tts = TextToSpeech(requireContext()) { if (it == TextToSpeech.SUCCESS) ttsReady = true } }
    private fun speak(t: String) { if (ttsReady) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "AI") }
    private fun scrollToBottom() { chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) } }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    
    override fun onDestroyView() { 
        super.onDestroyView()
        speechRecognizer?.destroy()
        tts?.shutdown()
        activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)?.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
    }
}