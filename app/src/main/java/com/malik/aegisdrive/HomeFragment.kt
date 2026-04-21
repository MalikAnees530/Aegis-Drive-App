package com.malik.aegisdrive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar
import java.util.Locale

@SuppressLint("SetTextI18n", "CommitPrefEdits")
class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private var profileListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var sessionListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var latestSessionId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            setupClickListeners(view)
            checkAIModelStatus(view)
            startRealTimeSync(view)
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated failed", e)
        }
    }

    private fun startRealTimeSync(view: View) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 🚀 LISTENER 1: REAL-TIME PROFILE GREETING (Lowercase 'users')
        profileListener?.remove()
        profileListener = db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists() && isAdded) {
                val name = snapshot.getString("name") ?: "Operator"
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 0..11 -> "Good Morning"
                    in 12..16 -> "Good Afternoon"
                    in 17..20 -> "Good Evening"
                    else -> "Good Night"
                }
                activity?.runOnUiThread {
                    view.findViewById<TextView>(R.id.tvGreeting)?.text = "$greeting, $name"
                }
            }
        }

        // 🚀 LISTENER 2: RECENT DRIVE METRICS & INDEX ERROR CATCH
        sessionListener?.remove()
        sessionListener = db.collection("DriveSessions")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException && 
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        Log.e("Aegis_CRITICAL", "MISSING INDEX: Build it here -> ${e.message}")
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null && isAdded) {
                    if (snapshots.isEmpty) {
                        activity?.runOnUiThread {
                            updateUIComponents(view, 0, 0, 0, "No driving history", 0, 100.0)
                        }
                    } else {
                        val docs = snapshots.documents
                        val last = docs[0]
                        latestSessionId = last.id // Capture for click listener
                        
                        try {
                            val score = last.getLong("score")?.toInt() ?: 100
                            val alerts = last.getLong("alerts")?.toInt() ?: 0
                            val duration = last.getLong("duration")?.toInt() ?: 0
                            val focus = last.getLong("focusLevel")?.toInt() ?: 100
                            val date = last.getString("dateString") ?: "Recent Session"

                            var totalScoreSum = 0
                            for (doc in docs) {
                                totalScoreSum += doc.getLong("score")?.toInt() ?: 0
                            }
                            val avgScore = totalScoreSum.toDouble() / docs.size

                            activity?.runOnUiThread {
                                updateUIComponents(view, score, alerts, duration, date, docs.size, avgScore)
                            }
                        } catch (ex: Exception) {
                            Log.e("Aegis", "Data parse error", ex)
                        }
                    }
                }
            }
    }

    private fun updateUIComponents(view: View, score: Int, alerts: Int, seconds: Int, date: String, sessionCount: Int, lifetimeSafety: Double) {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        
        val formattedDuration = if (h > 0) String.format(Locale.US, "%dh %dm %ds", h, m, s) else String.format(Locale.US, "%dm %ds", m, s)

        view.findViewById<TextView>(R.id.tvScoreValue)?.text = score.toString()
        view.findViewById<TextView>(R.id.tvDriveTimeValue)?.text = formattedDuration
        view.findViewById<TextView>(R.id.tvAlertsValue)?.text = alerts.toString()
        
        val focusLevel = if (sessionCount > 0) (score - (alerts * 4)).coerceIn(0, 100) else 100
        view.findViewById<TextView>(R.id.tvFocusValue)?.text = "$focusLevel%"
        view.findViewById<TextView>(R.id.tvRatingValue)?.text = String.format(Locale.US, "%.1f%%", lifetimeSafety)

        val tvHistoryTitle = view.findViewById<TextView>(R.id.tvHistoryTitle)
        val tvHistoryDate = view.findViewById<TextView>(R.id.tvHistoryDate)
        if (sessionCount > 0) {
            tvHistoryTitle?.text = "Session $sessionCount: $score%"
            tvHistoryDate?.text = date
        } else {
            tvHistoryTitle?.text = "No Recent Sessions"
            tvHistoryDate?.text = "Complete a drive to see data."
        }

        val colorHex = when {
            sessionCount == 0 -> "#94A3B8"
            score > 75 -> "#6ABF69"
            score > 45 -> "#FFB74D"
            else       -> "#EF5350"
        }
        val parsedColor = colorHex.toColorInt()
        view.findViewById<TextView>(R.id.tvScoreValue)?.setTextColor(parsedColor)
        val pb = view.findViewById<ProgressBar>(R.id.scoreProgress)
        pb?.progress = if (sessionCount == 0) 0 else score
        pb?.progressTintList = ColorStateList.valueOf(parsedColor)

        val tvStatus = view.findViewById<TextView>(R.id.tvStatusText)
        tvStatus?.text = if (sessionCount == 0) "NEW" else if (score > 75) "SAFE" else if (score > 45) "WARNING" else "DANGER"
        tvStatus?.setTextColor(parsedColor)
        view.findViewById<View>(R.id.statusIndicator)?.backgroundTintList = ColorStateList.valueOf(parsedColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        profileListener?.remove()
        sessionListener?.remove()
    }

    private fun checkAIModelStatus(view: View) {
        val tvAIStatus = view.findViewById<TextView>(R.id.tvAIStatus)
        val tvAIStatusMessage = view.findViewById<TextView>(R.id.tvAIStatusMessage)
        try {
            val assetFiles = requireContext().assets.list("")
            val hasLSTM = assetFiles?.contains("aegis_drive_model.tflite") == true
            val hasFaceMesh = assetFiles?.contains("face_landmarker.task") == true

            if (hasLSTM && hasFaceMesh) {
                tvAIStatus?.text = "ONLINE"
                tvAIStatus?.setTextColor("#6ABF69".toColorInt())
                tvAIStatusMessage?.text = "Shields active • Models Loaded"
            } else {
                tvAIStatus?.text = "OFFLINE"
                tvAIStatus?.setTextColor("#EF5350".toColorInt())
                tvAIStatusMessage?.text = "Models Missing"
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI Model check failed", e)
        }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.btnStartMonitoring)?.setOnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
            bottomNav?.selectedItemId = R.id.monitorFragment
        }
        view.findViewById<MaterialCardView>(R.id.btnSettings)?.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<MaterialCardView>(R.id.btnNotif)?.setOnClickListener {
            try {
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_security_logs, null)
                val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
                    .setView(dialogView)
                    .create()
                dialogView.findViewById<View>(R.id.btnConfirm)?.setOnClickListener { dialog.dismiss() }
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            } catch (e: Exception) {
                Log.e(TAG, "Notification dialog inflation failed", e)
            }
        }
        view.findViewById<TextView>(R.id.btnViewAllSessions)?.setOnClickListener {
            startActivity(Intent(requireContext(), SessionHistoryActivity::class.java))
        }

        // 🚀 WIRE LATEST SESSION CARD
        view.findViewById<View>(R.id.cardRecentSession)?.setOnClickListener {
            if (latestSessionId.isNotEmpty()) {
                SessionDetailBottomSheet(latestSessionId).show(parentFragmentManager, "SessionDetail")
            } else {
                AegisNotify.show(requireContext(), "No session intelligence available.", AegisNotify.Type.INFO)
            }
        }
    }
}
