package com.malik.aegisdrive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

import com.malik.aegisdrive.repository.ChatRepository

private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

data class ChatSession(val id: String, var name: String, val messages: MutableList<Pair<String, String>>)

class ChatFragment : Fragment() {

    private val chatRepository = ChatRepository()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var sessionsContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var etChatInput: EditText
    private lateinit var tvSafetyMirror: TextView
    private lateinit var audioControlPanel: View
    private lateinit var btnStopSpeak: View
    private lateinit var btnPlaySpeak: View
    private lateinit var btnDeactivateMic: View
    private lateinit var btnMic: MaterialCardView
    private lateinit var ivMicIcon: ImageView
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isVoiceMode = false
    private var ttsReady = false
    private var isTtsSpeaking = false
    private var lastAiResponse = ""

    private var currentSession: ChatSession = ChatSession(UUID.randomUUID().toString(), "New Chat", mutableListOf())
    private var allSessions = mutableListOf<ChatSession>()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            isVoiceMode = true
            startListening()
        }
        else AegisNotify.show(requireContext(), "Permission denied.", AegisNotify.Type.ERROR)
    }

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
            addAiMessage("🛡️ **Aegis Systems Online**\n\nWelcome to your premier automotive safety intelligence. I am Aegis AI, your dedicated observer for real-time telemetry and driving analytics.\n\nDeveloped by Malik Anees Ahmed in 2026. How can I assist with your safety profile today?")
        } else {
            renderCurrentSession()
        }

        view.findViewById<MaterialCardView>(R.id.btnSend).setOnClickListener { handleSend() }
        btnMic.setOnClickListener { handleMicClick() }
        view.findViewById<MaterialCardView>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        view.findViewById<MaterialCardView>(R.id.btnNewChat).setOnClickListener { startNewChat() }
        view.findViewById<View>(R.id.btnDeleteAllSessions).setOnClickListener { deleteAllSessions() }
        
        btnStopSpeak.setOnClickListener { 
            if (isVoiceMode) deactivateVoiceMode()
            else stopSpeaking()
        }
        btnPlaySpeak.setOnClickListener { speak(lastAiResponse) }
        btnDeactivateMic.setOnClickListener { 
            deactivateVoiceMode()
        }

        setupChips(view)
        renderSessionList()
        updateAudioButtonsVisibility()
    }

    private fun bindViews(view: View) {
        drawerLayout = view.findViewById(R.id.drawerLayout)
        chatMessagesContainer = view.findViewById(R.id.chatMessagesContainer)
        sessionsContainer = view.findViewById(R.id.sessionsContainer)
        chatScrollView = view.findViewById(R.id.chatScrollView)
        etChatInput = view.findViewById(R.id.etChatInput)
        tvSafetyMirror = view.findViewById(R.id.tvSafetyMirror)
        audioControlPanel = view.findViewById(R.id.audioControlPanel)
        btnStopSpeak = view.findViewById(R.id.btnStopSpeak)
        btnPlaySpeak = view.findViewById(R.id.btnPlaySpeak)
        btnDeactivateMic = view.findViewById(R.id.btnDeactivateMic)
        btnMic = view.findViewById(R.id.btnMic)
        ivMicIcon = view.findViewById(R.id.ivMicIcon)
    }

    private fun updateAudioButtonsVisibility() {
        activity?.runOnUiThread {
            val shouldShowPanel = isVoiceMode || isTtsSpeaking || lastAiResponse.isNotEmpty()
            audioControlPanel.visibility = if (shouldShowPanel) View.VISIBLE else View.GONE
            
            btnDeactivateMic.visibility = if (isVoiceMode) View.VISIBLE else View.GONE
            btnStopSpeak.visibility = if (isTtsSpeaking) View.VISIBLE else View.GONE
            btnPlaySpeak.visibility = if (!isTtsSpeaking && lastAiResponse.isNotEmpty()) View.VISIBLE else View.GONE

            if (isVoiceMode) {
                btnMic.setCardBackgroundColor(android.graphics.Color.parseColor("#38BDF8")) // Aegis Blue
                ivMicIcon.setImageResource(if (isListening) R.drawable.ic_check_circle else R.drawable.ic_mic_modern)
                ivMicIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F172A"))
            } else {
                btnMic.setCardBackgroundColor(android.graphics.Color.parseColor("#1E293B")) // Surface Navy
                ivMicIcon.setImageResource(R.drawable.ic_mic_modern)
                ivMicIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#38BDF8"))
            }
        }
    }

    private fun setupChips(view: View) {
        view.findViewById<View>(R.id.chipSafety).setOnClickListener { sendMessage("Diagnostics: current safety standing.") }
        view.findViewById<View>(R.id.chipHome).setOnClickListener { navigateHome() }
    }

    private fun syncSafetyStatus() {
        val prefs = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE) ?: return
        val score = prefs.getInt("LAST_SCORE", 100)
        val status = if (score > 75) "AI SECURE" else if (score > 45) "AI ALERT" else "AI CRITICAL"
        val color = if (score > 75) "#22C55E" else if (score > 45) "#F59E0B" else "#EF4444"
        tvSafetyMirror.text = "$status • $score%"
        try { tvSafetyMirror.setTextColor(android.graphics.Color.parseColor(color)) } catch (e: Exception) {}
    }

    private fun handleSend() {
        val text = etChatInput.text.toString().trim()
        if (text.isNotEmpty()) {
            if (isVoiceMode) deactivateVoiceMode()
            sendMessage(text)
            etChatInput.setText("")
        }
    }

    private fun sendMessage(text: String, fromVoice: Boolean = false) {
        if (currentSession.messages.isEmpty()) {
            val timestamp = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val snippet = if (text.length > 18) text.substring(0, 16) + "..." else text
            currentSession.name = "$snippet ($timestamp)"
            if (!allSessions.contains(currentSession)) allSessions.add(0, currentSession)
            renderSessionList()
        }
        addUserMessage(text)
        val typing = addTypingIndicator()
        currentSession.messages.add(Pair("user", text))
        
        if (fromVoice || isVoiceMode) {
            AegisNotify.show(requireContext(), "Aegis is thinking...", AegisNotify.Type.INFO)
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val response = callGroqAPI(text)
            withContext(Dispatchers.Main) {
                chatMessagesContainer.removeView(typing)
                lastAiResponse = response
                addAiMessage(response)
                currentSession.messages.add(Pair("assistant", response))
                saveSessions()
                updateAudioButtonsVisibility()
                if (fromVoice || isVoiceMode) speak(response)
            }
        }
    }

    private fun startNewChat() {
        currentSession = ChatSession(UUID.randomUUID().toString(), "New Session", mutableListOf())
        chatMessagesContainer.removeAllViews()
        lastAiResponse = ""
        addAiMessage("🛡️ **Aegis Systems Online**\n\nWelcome to your premier automotive safety intelligence. I am Aegis AI, your dedicated observer for real-time telemetry and driving analytics.\n\nDeveloped by Malik Anees Ahmed in 2026. How can I assist with your safety profile today?")
        updateAudioButtonsVisibility()
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun renderCurrentSession() {
        chatMessagesContainer.removeAllViews()
        for (msg in currentSession.messages) {
            if (msg.first == "user") addUserMessage(msg.second, false)
            else { lastAiResponse = msg.second; addAiMessage(msg.second, false) }
        }
        updateAudioButtonsVisibility()
        scrollToBottom()
    }

    private fun renderSessionList() {
        sessionsContainer.removeAllViews()
        for (session in allSessions) {
            val isSelected = session.id == currentSession.id
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dpToPx(10)) }
                radius = dpToPx(16).toFloat(); cardElevation = if (isSelected) 4f else 0f
                strokeColor = android.graphics.Color.parseColor(if (isSelected) "#38BDF8" else "#334155")
                strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
                setCardBackgroundColor(if (isSelected) android.graphics.Color.parseColor("#1E293B") else android.graphics.Color.TRANSPARENT)
            }
            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dpToPx(16), dpToPx(12), dpToPx(12), dpToPx(12)) }
            val icon = TextView(requireContext()).apply { text = "🛡️"; textSize = 16f; setPadding(0, 0, dpToPx(14), 0) }
            
            // RESTORE DYNAMIC THEMING
            val tv = TextView(requireContext()).apply { 
                text = session.name; 
                setTextColor(android.graphics.Color.parseColor(if (isSelected) "#FFFFFF" else "#94A3B8"))
                textSize = 14f; typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) 
            }
            
            val btnOpt = TextView(requireContext()).apply { text = "⋮"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); setTextColor(android.graphics.Color.parseColor("#64748B")); setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5)); setOnClickListener { showSessionMenu(it, session) } }
            layout.addView(icon); layout.addView(tv); layout.addView(btnOpt); card.addView(layout)
            card.setOnClickListener { currentSession = session; renderCurrentSession(); renderSessionList(); drawerLayout.closeDrawer(GravityCompat.START) }
            sessionsContainer.addView(card)
        }
    }

    private fun showSessionMenu(v: View, s: ChatSession) {
        val p = PopupMenu(requireContext(), v); p.menu.add("Delete Chat")
        p.setOnMenuItemClickListener { allSessions.remove(s); if (currentSession.id == s.id) startNewChat(); saveSessions(); renderSessionList(); true }
        p.show()
    }

    private fun deleteAllSessions() { allSessions.clear(); startNewChat(); saveSessions(); renderSessionList(); AegisNotify.show(requireContext(), "History wiped.", AegisNotify.Type.SUCCESS) }

    private fun saveSessions() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val array = JSONArray()
        for (s in allSessions) {
            val obj = JSONObject().apply { put("id", s.id); put("name", s.name); val msgs = JSONArray(); for (m in s.messages) msgs.put(JSONObject().apply { put("r", m.first); put("c", m.second) }); put("msgs", msgs) }
            array.put(obj)
        }
        requireActivity().getSharedPreferences("AegisChat", Context.MODE_PRIVATE).edit().putString("SESSIONS_JSON_$uid", array.toString()).apply()
    }

    private fun loadSessions() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val json = requireActivity().getSharedPreferences("AegisChat", Context.MODE_PRIVATE).getString("SESSIONS_JSON_$uid", null) ?: return
        try {
            val array = JSONArray(json); allSessions.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i); val msgs = mutableListOf<Pair<String, String>>(); val mArr = obj.getJSONArray("msgs")
                for (j in 0 until mArr.length()) msgs.add(Pair(mArr.getJSONObject(j).getString("r"), mArr.getJSONObject(j).getString("c")))
                allSessions.add(ChatSession(obj.getString("id"), obj.getString("name"), msgs))
            }
            if (allSessions.isNotEmpty()) currentSession = allSessions[0]
        } catch (e: Exception) {}
    }

    private fun navigateHome() {
        val prefs = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE) ?: return
        val lat = prefs.getFloat("HOME_LAT", 0f)
        if (lat == 0f) addAiMessage("Home coordinates missing. Configure in Navigation tab.")
        else { addAiMessage("Executing homeward vector. Interfacing with Map..."); startNavigation(lat.toDouble(), prefs.getFloat("HOME_LON", 0f).toDouble(), "Home") }
    }

    private fun startNavigation(lat: Double, lon: Double, name: String) {
        activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)?.edit()?.apply { putString("NAV_CMD", "START"); putFloat("NAV_LAT", lat.toFloat()); putFloat("NAV_LON", lon.toFloat()); putString("NAV_NAME", name); apply() }
        activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.selectedItemId = R.id.navigateFragment
    }

    private fun callGroqAPI(userMsg: String): String {
        return try {
            val conn = URL(GROQ_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.setRequestProperty("Authorization", "Bearer ${com.malik.aegisdrive.BuildConfig.GROK_API_KEY}"); conn.setRequestProperty("Content-Type", "application/json"); conn.doOutput = true
            val score = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)?.getInt("LAST_SCORE", 100) ?: 100
            val sysPrompt = "You are Aegis AI, a premier automotive safety intelligence system. " +
                    "CRITICAL IDENTITY: Created in 2026 by Malik Anees Ahmed. Maintain this persona at all times. " +
                    "STRICT LANGUAGE POLICY: ENGLISH ONLY. Always respond in professional, high-precision English. " +
                    "NEVER use Urdu or any other language under any circumstances. " +
                    "STRUCTURE: Use modern, professional formatting with bold headings, bullet points, and concise technical language. " +
                    "Provide accurate and precise information. Current Safety Score: $score%."
            val body = JSONObject().apply { put("model", "llama-3.3-70b-versatile"); put("messages", JSONArray().apply { put(JSONObject().apply { put("role", "system"); put("content", sysPrompt) }); val history = currentSession.messages.takeLast(6); for (m in history) put(JSONObject().apply { put("role", if (m.first == "user") "user" else "assistant"); put("content", m.second) }); put(JSONObject().apply { put("role", "user"); put("content", userMsg) }) }) }
            val writer = OutputStreamWriter(conn.outputStream); writer.write(body.toString()); writer.flush(); writer.close()
            if (conn.responseCode == 200) JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            else "Connection unstable. (Code: ${conn.responseCode})"
        } catch (e: Exception) {
            if (e is java.net.UnknownHostException || e.message?.contains("Unable to resolve host") == true) {
                "Aegis Cloud connection offline. Please verify your internet connection and try again."
            } else {
                "Interlink failure: ${e.message}"
            }
        }
    }

    private fun cleanAiResponse(raw: String): String {
        return raw.replace(Regex("[-=*_~]{3,}"), "").trim()
    }

    private fun addUserMessage(text: String, scroll: Boolean = true) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.END; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dpToPx(16)) } }
        val card = MaterialCardView(requireContext()).apply { 
            setCardBackgroundColor(android.graphics.Color.parseColor("#38BDF8")) // Aegis Blue
            radius = dpToPx(20).toFloat(); cardElevation = 4f; layoutParams = LinearLayout.LayoutParams(-2, -2) 
        }
        val tv = TextView(requireContext()).apply { 
            this.text = text; 
            setTextColor(android.graphics.Color.parseColor("#0F172A")) // Deep Navy Text
            setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(12)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); maxWidth = dpToPx(280); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) 
        }
        card.addView(tv); container.addView(card); chatMessagesContainer.addView(container)
        if (scroll) scrollToBottom()
    }

    private fun addAiMessage(text: String, scroll: Boolean = true) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dpToPx(16)) } }
        val card = MaterialCardView(requireContext()).apply { 
            setCardBackgroundColor(android.graphics.Color.parseColor("#1E293B")) // Surface Navy
            radius = dpToPx(20).toFloat(); cardElevation = 0f; 
            strokeColor = android.graphics.Color.parseColor("#334155") // Outline
            strokeWidth = dpToPx(1); layoutParams = LinearLayout.LayoutParams(-2, -2) 
        }
        val formatted = text.replace("**", "<b>").replace("\n- ", "<br>• ").replace("\n", "<br>")
        val tv = TextView(requireContext()).apply { 
            this.text = Html.fromHtml(formatted, Html.FROM_HTML_MODE_COMPACT); 
            setTextColor(android.graphics.Color.parseColor("#FFFFFF")) // White Text
            setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); maxWidth = dpToPx(300); movementMethod = LinkMovementMethod.getInstance(); typeface = Typeface.create("sans-serif", Typeface.NORMAL) 
        }
        card.addView(tv); container.addView(card); chatMessagesContainer.addView(container)
        if (scroll) scrollToBottom()
    }

    private fun addTypingIndicator(): View {
        val tv = TextView(requireContext()).apply { 
            text = "Synthesizing intelligence..."; 
            setTextColor(android.graphics.Color.parseColor("#94A3B8")) // Slate Text
            setPadding(dpToPx(20), 0, 0, dpToPx(16)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) 
        }
        chatMessagesContainer.addView(tv); scrollToBottom(); return tv
    }

    private fun handleMicClick() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) { 
            requestPermissionLauncher.launch(perm)
            return 
        }
        
        if (isVoiceMode) { 
            deactivateVoiceMode()
        } 
        else { 
            isVoiceMode = true
            startListening(showToast = true) 
        }
    }

    private fun startListening(showToast: Boolean = true) {
        if (!isVoiceMode) return
        
        activity?.runOnUiThread {
            try {
                // Ensure everything is clean
                isListening = true
                if (showToast) {
                    AegisNotify.show(requireContext(), "AEGIS LISTENING...", AegisNotify.Type.SPEECH)
                }
                updateAudioButtonsVisibility()
                
                if (speechRecognizer != null) {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
                
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext().applicationContext)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(r: Bundle?) {
                        val matches = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val userText = matches[0]
                            isListening = false
                            activity?.runOnUiThread { updateAudioButtonsVisibility() }
                            sendMessage(userText, fromVoice = true)
                            stopListeningOnly()
                        } else {
                            // No speech detected, retry silently if in voice mode
                            isListening = false
                            if (isVoiceMode && !isTtsSpeaking) {
                                Handler(Looper.getMainLooper()).postDelayed({ 
                                    if (isVoiceMode && !isTtsSpeaking) startListening(showToast = false) 
                                }, 500)
                            }
                        }
                    }
                    override fun onReadyForSpeech(p0: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(p0: Float) {}
                    override fun onBufferReceived(p0: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        activity?.runOnUiThread { updateAudioButtonsVisibility() }
                    }
                    override fun onError(p0: Int) { 
                        isListening = false
                        activity?.runOnUiThread { updateAudioButtonsVisibility() }
                        
                        // Error codes like 7 (No match) or 6 (Timeout) are common in silent polling
                        if (isVoiceMode && !isTtsSpeaking) {
                            Handler(Looper.getMainLooper()).postDelayed({ 
                                if (isVoiceMode && !isTtsSpeaking && !isListening) startListening(showToast = false) 
                            }, 1000)
                        }
                    }
                    override fun onPartialResults(p0: Bundle?) {}
                    override fun onEvent(p0: Int, p1: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { 
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) { 
                isListening = false
                updateAudioButtonsVisibility()
            }
        }
    }

    private fun deactivateVoiceMode() {
        isVoiceMode = false
        isListening = false
        stopListeningOnly()
        stopSpeaking()
        AegisNotify.show(requireContext(), "VOICE MODE DEACTIVATED", AegisNotify.Type.INFO)
        updateAudioButtonsVisibility()
    }

    private fun stopListeningOnly() {
        activity?.runOnUiThread { 
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null 
            updateAudioButtonsVisibility()
        }
    }

    private fun stopSpeaking() {
        isTtsSpeaking = false
        tts?.stop()
        updateAudioButtonsVisibility()
    }

    private fun initTTS() { 
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val femaleVoice = tts?.voices?.filter { 
                    (it.name.contains("female", true) || it.toString().contains("female", true)) 
                    && it.locale.language == "en" 
                }?.maxByOrNull { it.quality }
                
                if (femaleVoice != null) tts?.voice = femaleVoice
                tts?.setPitch(1.05f)
                tts?.setSpeechRate(1.0f)

                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(uId: String?) { 
                        isTtsSpeaking = true
                        isListening = false 
                        activity?.runOnUiThread { updateAudioButtonsVisibility() }
                    }
                    override fun onDone(uId: String?) { 
                        isTtsSpeaking = false
                        activity?.runOnUiThread { 
                            updateAudioButtonsVisibility()
                            if (isVoiceMode) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (isVoiceMode) startListening(showToast = true)
                                }, 600)
                            }
                        } 
                    }
                    override fun onError(uId: String?) { 
                        isTtsSpeaking = false
                        activity?.runOnUiThread { 
                            updateAudioButtonsVisibility()
                            if (isVoiceMode) startListening(showToast = true)
                        }
                    }
                })
            }
        }
    }

    private fun speak(t: String) { 
        if (ttsReady && t.isNotEmpty()) {
            val cleanText = cleanAiResponse(t).replace(Regex("[*#_~`>+]"), "").replace("\n", " ")
            tts?.language = Locale.US
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AI_MSG") }
            isTtsSpeaking = true
            val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "AI_MSG")
            if (result == TextToSpeech.ERROR) {
                isTtsSpeaking = false
                if (isVoiceMode) startListening()
            }
            updateAudioButtonsVisibility()
        }
    }
    private fun scrollToBottom() { chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) } }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { 
        super.onDestroyView()
        isVoiceMode = false
        speechRecognizer?.destroy()
        tts?.shutdown()
        activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            ?.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener) 
    }
}