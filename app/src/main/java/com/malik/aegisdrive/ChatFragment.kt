package com.malik.aegisdrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment() {

    // ── GROQ API config ──────────────────────────────────────────────────────────
    private val GROQ_API_KEY = "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"
    private val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

    private val SYSTEM_PROMPT = "You are Aegis AI, the intelligent driving assistant built " +
            "into the AegisDrive app. You help drivers with safety advice, navigation tips, " +
            "driving scores, alerts, and any general questions. Keep your responses VERY concise, short, and conversational, like a real voice assistant. " +
            "Always prioritize driver safety."

    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var etChatInput: EditText
    private lateinit var btnSend: MaterialCardView
    private lateinit var btnMic: MaterialCardView
    private lateinit var btnClearChat: MaterialCardView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var ttsReady = false

    // State variable for Live Mode
    private var isLiveMode = false

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    companion object {
        private const val MIC_PERMISSION_CODE = 101
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatMessagesContainer = view.findViewById(R.id.chatMessagesContainer)
        chatScrollView        = view.findViewById(R.id.chatScrollView)
        etChatInput           = view.findViewById(R.id.etChatInput)
        btnSend               = view.findViewById(R.id.btnSend)
        btnMic                = view.findViewById(R.id.btnMic)
        btnClearChat          = view.findViewById(R.id.btnClearChat)

        chatMessagesContainer.removeAllViews()
        addAiMessage("Hello! I am Aegis AI. Ask me anything about driving safety, navigation, or anything else. I am here to help!")

        setupQuickChips(view)

        btnSend.setOnClickListener {
            val text = etChatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                disableLiveMode()
                sendMessage(text, isVoiceInput = false)
                etChatInput.setText("")
            }
        }

        etChatInput.setOnEditorActionListener { _, _, _ ->
            val text = etChatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                disableLiveMode()
                sendMessage(text, isVoiceInput = false)
                etChatInput.setText("")
            }
            true
        }

        btnMic.setOnClickListener { handleMicClick() }

        btnClearChat.setOnClickListener {
            chatMessagesContainer.removeAllViews()
            conversationHistory.clear()
            disableLiveMode()
            addAiMessage("Chat cleared! How can I help you?")
        }

        initTTS()
    }

    private fun disableLiveMode() {
        isLiveMode = false
        stopListening()
        tts?.stop()
    }

    private fun setupQuickChips(view: View) {
        val scrollView = view.findViewById<HorizontalScrollView>(R.id.quickChipsRow) ?: return
        val chipsContainer = scrollView.getChildAt(0) as? LinearLayout ?: return
        val chipTexts = listOf("My safety score", "Navigate home", "Nearest hospital")

        for (i in 0 until chipsContainer.childCount) {
            val chip = chipsContainer.getChildAt(i) as? MaterialCardView ?: continue
            val label = chipTexts.getOrNull(i) ?: continue
            chip.setOnClickListener {
                disableLiveMode()
                sendMessage(label, isVoiceInput = false)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  TEXT TO SPEECH (VOICE QUALITY & MIC LOOP FIX)
    // ══════════════════════════════════════════════════════════════
    private fun initTTS() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US

                // 🚀 FIX 2: Voice Tuning (Less Robotic)
                tts?.setPitch(1.1f)      // Slightly higher pitch for a friendly tone
                tts?.setSpeechRate(0.95f) // Slightly slower so it's easy to understand

                ttsReady = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (isLiveMode && utteranceId == "AI_RESPONSE") {
                            // 🚀 FIX 1: Add a 500ms delay so Mic and Speaker don't collide
                            activity?.runOnUiThread {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    startListening()
                                }, 500)
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (isLiveMode) {
                            activity?.runOnUiThread {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    startListening()
                                }, 500)
                            }
                        }
                    }
                })
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            val clean = text.replace(Regex("[*_#`]"), "")
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE")
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  VOICE INPUT
    // ══════════════════════════════════════════════════════════════
    private fun handleMicClick() {
        val permission = android.Manifest.permission.RECORD_AUDIO
        if (requireContext().checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), MIC_PERMISSION_CODE)
            return
        }

        if (isLiveMode || isListening) {
            disableLiveMode()
            Toast.makeText(requireContext(), "Live Mode Disabled", Toast.LENGTH_SHORT).show()
        } else {
            isLiveMode = true
            Toast.makeText(requireContext(), "Live Mode Enabled", Toast.LENGTH_SHORT).show()
            startListening()
        }
    }

    private fun startListening() {
        if (isListening) return // Safety check to prevent double listening

        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
        if (isEmulator || !SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            showVoiceInputDialog()
            return
        }

        isListening = true
        setMicActive(true)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""

                isListening = false
                setMicActive(false)

                if (spokenText.isNotEmpty()) {
                    sendMessage(spokenText, isVoiceInput = true)
                } else if (isLiveMode) {
                    // Start listening again if no words were caught but live mode is on
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startListening()
                    }, 500)
                }
            }

            override fun onError(error: Int) {
                activity?.runOnUiThread {
                    isListening = false
                    setMicActive(false)

                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        isLiveMode = false
                        Toast.makeText(requireContext(), "Live Mode Paused (Silence)", Toast.LENGTH_SHORT).show()
                    } else {
                        showVoiceInputDialog()
                    }
                }
            }

            override fun onEndOfSpeech() {
                isListening = false
                setMicActive(false)
            }
        })

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            disableLiveMode()
            showVoiceInputDialog()
        }
    }

    private fun showVoiceInputDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Voice Emulator")
        builder.setMessage("Type message for Live Mode:")

        val input = android.widget.EditText(requireContext())
        input.setTextColor(resources.getColor(android.R.color.black, null))
        builder.setView(input)

        builder.setPositiveButton("Send") { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text, isVoiceInput = true)
        }
        builder.setNegativeButton("Cancel") { _, _ ->
            disableLiveMode()
        }
        builder.show()
    }

    private fun stopListening() {
        isListening = false
        activity?.runOnUiThread { setMicActive(false) }

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
        }
    }

    private fun setMicActive(active: Boolean) {
        val mic = view?.findViewById<MaterialCardView>(R.id.btnMic) ?: return
        val color = if (active || isLiveMode)
            resources.getColor(R.color.status_danger, null)
        else
            resources.getColor(R.color.accent_primary, null)
        mic.setCardBackgroundColor(color)
    }

    private fun sendMessage(userText: String, isVoiceInput: Boolean) {
        addUserMessage(userText)
        val typingView = addTypingIndicator()
        conversationHistory.add(Pair("user", userText))

        CoroutineScope(Dispatchers.IO).launch {
            val response = callGroqAPI(userText)
            withContext(Dispatchers.Main) {
                chatMessagesContainer.removeView(typingView)
                addAiMessage(response)

                if (isVoiceInput) {
                    speak(response)
                } else {
                    disableLiveMode()
                }

                conversationHistory.add(Pair("assistant", response))
            }
        }
    }

    private fun callGroqAPI(userMessage: String): String {
        return try {
            val url  = URL(GROQ_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput     = true
            conn.connectTimeout = 15000
            conn.readTimeout    = 15000

            val messagesArray = JSONArray()

            val sysMsg = JSONObject()
            sysMsg.put("role", "system")
            sysMsg.put("content", SYSTEM_PROMPT)
            messagesArray.put(sysMsg)

            val history = if (conversationHistory.size > 6) conversationHistory.takeLast(6) else conversationHistory
            for ((role, text) in history) {
                val msg = JSONObject()
                msg.put("role", role)
                msg.put("content", text)
                messagesArray.put(msg)
            }

            val userMsgObj = JSONObject()
            userMsgObj.put("role", "user")
            userMsgObj.put("content", userMessage)
            messagesArray.put(userMsgObj)

            val body = JSONObject()
            body.put("model", "llama-3.3-70b-versatile")
            body.put("messages", messagesArray)
            body.put("temperature", 0.7)
            body.put("max_tokens", 512)

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val raw = conn.inputStream.bufferedReader().readText()
                    JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
                else -> {
                    val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "No details"
                    "API Error: $errorBody"
                }
            }
        } catch (e: Exception) {
            "Network error. Please try again."
        }
    }

    private fun addUserMessage(text: String) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, 0, 0, dpToPx(10)) }
        }

        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.accent_primary, null))
            radius = dpToPx(14).toFloat()
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize  = 12f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            maxWidth = dpToPx(240)
        }
        card.addView(tv)
        val time = TextView(requireContext()).apply {
            this.text = getCurrentTime()
            textSize  = 9f
            setTextColor(resources.getColor(R.color.text_disabled, null))
            setPadding(0, dpToPx(3), 0, 0)
        }
        container.addView(card)
        container.addView(time)
        chatMessagesContainer.addView(container)
        scrollToBottom()
    }

    private fun addAiMessage(text: String) {
        val container = LinearLayout(requireContext()).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, 0, 0, dpToPx(10)) }
        }

        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.bg_surface, null))
            radius      = dpToPx(14).toFloat()
            cardElevation = 0f
            strokeColor = resources.getColor(R.color.border_subtle, null)
            strokeWidth = dpToPx(1)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize  = 12f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            maxWidth = dpToPx(240)
        }
        card.addView(tv)
        val time = TextView(requireContext()).apply {
            this.text = getCurrentTime()
            textSize  = 9f
            setTextColor(resources.getColor(R.color.text_disabled, null))
            setPadding(0, dpToPx(3), 0, 0)
        }
        container.addView(card)
        container.addView(time)
        chatMessagesContainer.addView(container)
        scrollToBottom()
    }

    private fun addTypingIndicator(): View {
        val container = LinearLayout(requireContext()).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(0, 0, 0, dpToPx(10)) }
        }

        val card = MaterialCardView(requireContext()).apply {
            setCardBackgroundColor(resources.getColor(R.color.bg_surface, null))
            radius      = dpToPx(14).toFloat()
            cardElevation = 0f
            strokeColor = resources.getColor(R.color.border_subtle, null)
            strokeWidth = dpToPx(1)
        }

        val tv = TextView(requireContext()).apply {
            text     = "Aegis is thinking..."
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_disabled, null))
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
        }
        card.addView(tv)
        container.addView(card)
        chatMessagesContainer.addView(container)
        scrollToBottom()
        return container
    }

    private fun scrollToBottom() { chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) } }
    private fun getCurrentTime(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == MIC_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            Toast.makeText(requireContext(), "Microphone permission is needed for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}