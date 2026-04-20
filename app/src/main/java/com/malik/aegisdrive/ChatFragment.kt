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
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private val groqApiKey = "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"
    private val groqUrl = "https://api.groq.com/openai/v1/chat/completions"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var sessionsContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var etChatInput: EditText
    private lateinit var tvSafetyMirror: TextView
    private lateinit var audioControlPanel: View
    private lateinit var btnStopSpeak: View
    private lateinit var btnPlaySpeak: View
    private lateinit var ivMicIcon: ImageView
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isContinuousListening = false
    private var ttsReady = false
    private var lastAiResponse = ""

    private var currentSession: ChatSession = ChatSession(UUID.randomUUID().toString(), "New Chat", mutableListOf())
    private var allSessions = mutableListOf<ChatSession>()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startListening()
        else AegisNotify.show(requireContext(), "Permission Denied", AegisNotify.Type.ERROR)
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
            addAiMessage("Systems online. I am Aegis AI, your automotive intelligence observer created by Malik Anees Ahmed. Standing by for instructions.")
        } else {
            renderCurrentSession()
        }

        view.findViewById<MaterialCardView>(R.id.btnSend).setOnClickListener { handleSend() }
        view.findViewById<MaterialCardView>(R.id.btnMic).setOnClickListener { handleMicClick() }
        view.findViewById<MaterialCardView>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        view.findViewById<MaterialCardView>(R.id.btnNewChat).setOnClickListener { startNewChat() }
        view.findViewById<View>(R.id.btnDeleteAllSessions).setOnClickListener { deleteAllSessions() }
        
        btnStopSpeak.setOnClickListener { 
            stopListening()
            isContinuousListening = false
            tts?.stop()
            btnStopSpeak.visibility = View.GONE 
        }
        btnPlaySpeak.setOnClickListener { speak(lastAiResponse) }

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
        audioControlPanel = view.findViewById(R.id.audioControlPanel)
        btnStopSpeak = view.findViewById(R.id.btnStopSpeak)
        btnPlaySpeak = view.findViewById(R.id.btnPlaySpeak)
        ivMicIcon = view.findViewById(R.id.ivMicIcon)
    }

    private fun setupChips(view: View) {
        view.findViewById<View>(R.id.chipSafety).setOnClickListener { sendMessage("Run diagnostic on current safety standing.") }
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
            isContinuousListening = false
            sendMessage(text)
            etChatInput.setText("")
        }
    }

    private fun sendMessage(text: String, fromVoice: Boolean = false) {
        if (currentSession.messages.isEmpty()) {
            currentSession.name = if (text.length > 22) text.substring(0, 20) + "..." else text
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
                lastAiResponse = response
                addAiMessage(response)
                currentSession.messages.add(Pair("assistant", response))
                saveSessions()
                audioControlPanel.visibility = View.VISIBLE
                
                if (fromVoice) {
                    speak(response)
                    btnStopSpeak.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startNewChat() {
        currentSession = ChatSession(UUID.randomUUID().toString(), "New Intel Session", mutableListOf())
        chatMessagesContainer.removeAllViews()
        lastAiResponse = ""
        audioControlPanel.visibility = View.GONE
        addAiMessage("New analytics session initialized.")
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun renderCurrentSession() {
        chatMessagesContainer.removeAllViews()
        for (msg in currentSession.messages) {
            if (msg.first == "user") addUserMessage(msg.second, false)
            else { lastAiResponse = msg.second; addAiMessage(msg.second, false); audioControlPanel.visibility = View.VISIBLE }
        }
        scrollToBottom()
    }

    private fun renderSessionList() {
        sessionsContainer.removeAllViews()
        for (session in allSessions) {
            val isSelected = session.id == currentSession.id
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dpToPx(10)) }
                radius = dpToPx(16).toFloat()
                cardElevation = if (isSelected) 4f else 0f
                strokeColor = resources.getColor(if (isSelected) R.color.accent_primary else R.color.border_subtle, null)
                strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
                setCardBackgroundColor(if (isSelected) resources.getColor(R.color.bg_elevated, null) else android.graphics.Color.TRANSPARENT)
            }
            
            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dpToPx(16), dpToPx(12), dpToPx(12), dpToPx(12)) }
            val icon = TextView(requireContext()).apply { text = "🛡️"; textSize = 16f; setPadding(0, 0, dpToPx(14), 0) }
            val tv = TextView(requireContext()).apply { text = session.name; setTextColor(resources.getColor(R.color.text_primary, null)); textSize = 14f; typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            
            val btnOptions = TextView(requireContext()).apply {
                text = "⋮"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(resources.getColor(R.color.text_disabled, null))
                setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
                setOnClickListener { showSessionMenu(it, session) }
            }

            layout.addView(icon); layout.addView(tv); layout.addView(btnOptions)
            card.addView(layout)
            card.setOnClickListener { currentSession = session; renderCurrentSession(); renderSessionList(); drawerLayout.closeDrawer(GravityCompat.START) }
            sessionsContainer.addView(card)
        }
    }

    private fun showSessionMenu(anchor: View, session: ChatSession) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add("Delete Session")
        popup.setOnMenuItemClickListener {
            deleteSingleSession(session)
            true
        }
        popup.show()
    }

    private fun deleteSingleSession(session: ChatSession) {
        allSessions.remove(session)
        if (currentSession.id == session.id) startNewChat()
        saveSessions()
        renderSessionList()
        AegisNotify.show(requireContext(), "Intel session deleted.", AegisNotify.Type.SUCCESS)
    }

    private fun deleteAllSessions() { allSessions.clear(); startNewChat(); saveSessions(); renderSessionList(); AegisNotify.show(requireContext(), "Telemetry purged.", AegisNotify.Type.SUCCESS) }

    private fun saveSessions() {
        val array = JSONArray()
        for (s in allSessions) {
            val obj = JSONObject().apply { put("id", s.id); put("name", s.name); val msgs = JSONArray(); for (m in s.messages) msgs.put(JSONObject().apply { put("r", m.first); put("c", m.second) }); put("msgs", msgs) }
            array.put(obj)
        }
        requireActivity().getSharedPreferences("AegisChat", Context.MODE_PRIVATE).edit().putString("SESSIONS_JSON", array.toString()).apply()
    }

    private fun loadSessions() {
        val json = requireActivity().getSharedPreferences("AegisChat", Context.MODE_PRIVATE).getString("SESSIONS_JSON", null) ?: return
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
        if (lat == 0f) addAiMessage("Home coordinates missing. Calibrate in Navigation terminal.")
        else { addAiMessage("Executing homeward vector. Interfacing with Map..."); startNavigation(lat.toDouble(), prefs.getFloat("HOME_LON", 0f).toDouble(), "Home") }
    }

    private fun startNavigation(lat: Double, lon: Double, name: String) {
        activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)?.edit()?.apply { putString("NAV_CMD", "START"); putFloat("NAV_LAT", lat.toFloat()); putFloat("NAV_LON", lon.toFloat()); putString("NAV_NAME", name); apply() }
        activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.selectedItemId = R.id.navigateFragment
    }

    private fun callGroqAPI(userMsg: String): String {
        return try {
            val conn = URL(groqUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.setRequestProperty("Authorization", "Bearer $groqApiKey"); conn.setRequestProperty("Content-Type", "application/json"); conn.doOutput = true
            val score = activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)?.getInt("LAST_SCORE", 100) ?: 100
            
            val systemPrompt = "You are Aegis AI, a world-class automotive safety intelligence developed by Malik Anees Ahmed. " +
                    "Your responses MUST be professional and highly structured. Use bold headers, bullet points, and clear sections. " +
                    "Current Driver Safety: $score%."
            
            val body = JSONObject().apply { put("model", "llama-3.3-70b-versatile"); put("messages", JSONArray().apply { put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) }); val history = currentSession.messages.takeLast(6); for (m in history) put(JSONObject().apply { put("role", if (m.first == "user") "user" else "assistant"); put("content", m.second) }); put(JSONObject().apply { put("role", "user"); put("content", userMsg) }) }) }
            val writer = OutputStreamWriter(conn.outputStream); writer.write(body.toString()); writer.flush(); writer.close()
            if (conn.responseCode == 200) JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            else "Signal interference detected."
        } catch (e: Exception) { "Interlink failure." }
    }

    private fun addUserMessage(text: String, scroll: Boolean = true) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.END; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dpToPx(16)) } }
        val card = MaterialCardView(requireContext()).apply { setCardBackgroundColor(resources.getColor(R.color.accent_primary, null)); radius = dpToPx(20).toFloat(); cardElevation = 4f; layoutParams = LinearLayout.LayoutParams(-2, -2) }
        val tv = TextView(requireContext()).apply { this.text = text; setTextColor(resources.getColor(R.color.bg_deepest, null)); setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(12)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); maxWidth = dpToPx(280); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        card.addView(tv); container.addView(card); chatMessagesContainer.addView(container)
        if (scroll) scrollToBottom()
    }

    private fun addAiMessage(text: String, scroll: Boolean = true) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dpToPx(16)) } }
        val card = MaterialCardView(requireContext()).apply { setCardBackgroundColor(resources.getColor(R.color.bg_surface, null)); radius = dpToPx(20).toFloat(); cardElevation = 0f; strokeColor = resources.getColor(R.color.border_subtle, null); strokeWidth = dpToPx(1); layoutParams = LinearLayout.LayoutParams(-2, -2) }
        val formatted = text.replace("**", "<b>").replace("\n- ", "<br>• ").replace("\n", "<br>")
        val tv = TextView(requireContext()).apply { this.text = Html.fromHtml(formatted, Html.FROM_HTML_MODE_COMPACT); setTextColor(resources.getColor(R.color.text_primary, null)); setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); maxWidth = dpToPx(300); movementMethod = LinkMovementMethod.getInstance(); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        card.addView(tv); container.addView(card); chatMessagesContainer.addView(container)
        if (scroll) scrollToBottom()
    }

    private fun addTypingIndicator(): View {
        val tv = TextView(requireContext()).apply { text = "Analysing telemetry..."; setTextColor(resources.getColor(R.color.text_disabled, null)); setPadding(dpToPx(20), 0, 0, dpToPx(16)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) }
        chatMessagesContainer.addView(tv); scrollToBottom(); return tv
    }

    private fun handleMicClick() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (requireContext().checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) { requestPermissionLauncher.launch(perm); return }
        if (isListening) { isContinuousListening = false; stopListening() } else { isContinuousListening = true; startListening() }
    }

    private fun startListening() {
        try {
            isListening = true
            ivMicIcon.setImageResource(R.drawable.ic_check_circle)
            ivMicIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val matches = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) sendMessage(matches[0], fromVoice = true)
                    if (isContinuousListening) { Handler(Looper.getMainLooper()).postDelayed({ if (isContinuousListening) startListening() }, 500) } 
                    else stopListening()
                }
                override fun onReadyForSpeech(p0: Bundle?) { AegisNotify.show(requireContext(), "AI INTELLIGENCE LISTENING...", AegisNotify.Type.SPEECH) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(p0: Int) { if (isContinuousListening) startListening() else stopListening() }
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) { stopListening() }
    }

    private fun stopListening() { 
        isListening = false
        ivMicIcon.setImageResource(R.drawable.ic_mic)
        ivMicIcon.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent_primary, null))
        activity?.runOnUiThread { speechRecognizer?.stopListening(); speechRecognizer?.destroy(); speechRecognizer = null }
    }

    private fun initTTS() { 
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(uId: String?) { activity?.runOnUiThread { btnStopSpeak.visibility = View.VISIBLE } }
                    override fun onDone(uId: String?) { activity?.runOnUiThread { btnStopSpeak.visibility = View.GONE } }
                    override fun onError(uId: String?) { activity?.runOnUiThread { btnStopSpeak.visibility = View.GONE } }
                })
            }
        }
    }

    private fun speak(t: String) { if (ttsReady) { val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AI_MSG") }; tts?.speak(t, TextToSpeech.QUEUE_FLUSH, params, "AI_MSG") } }
    private fun scrollToBottom() { chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) } }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); isContinuousListening = false; speechRecognizer?.destroy(); tts?.shutdown(); activity?.getSharedPreferences("AegisData", Context.MODE_PRIVATE)?.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener) }
}