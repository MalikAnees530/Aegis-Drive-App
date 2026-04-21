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
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated failed", e)
        }
    }

    override fun onStart() {
        super.onStart()
        view?.let { startRealTimeSync(it) }
    }

    override fun onStop() {
        super.onStop()
        profileListener?.remove()
        sessionListener?.remove()
    }

    private fun startRealTimeSync(view: View) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 🚀 LISTENER 1: REAL-TIME PROFILE & GREETING
        profileListener?.remove()
        profileListener = db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Profile sync failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists() && isAdded) {
                val displayName = snapshot.getString("displayName") ?: "Operator"
                
                // Extract lifetimeStats safely from the new schema
                val lifetimeStats = snapshot.get("lifetimeStats") as? Map<*, *>
                val avgSafetyScore = (lifetimeStats?.get("averageSafetyScore") as? Double) ?: 100.0

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 0..11 -> "Good Morning"
                    in 12..16 -> "Good Afternoon"
                    in 17..20 -> "Good Evening"
                    else -> "Good Night"
                }
                
                activity?.runOnUiThread {
                    view.findViewById<TextView>(R.id.tvGreeting)?.text = "$greeting, $displayName"
                    view.findViewById<TextView>(R.id.tvRatingValue)?.text = String.format(Locale.US, "%.1f%%", avgSafetyScore)
                }
            }
        }

        // 🚀 LISTENER 2: LATEST DRIVE SESSION (Subcollection)
        sessionListener?.remove()
        sessionListener = db.collection("users").document(uid)
            .collection("drive_sessions")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Latest session sync failed.", e)
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
                        latestSessionId = last.id 
                        
                        try {
                            val score = last.getLong("finalSafetyScore")?.toInt() ?: 100
                            val alerts = last.getLong("totalAlertsFired")?.toInt() ?: 0
                            val duration = last.getLong("durationSeconds")?.toInt() ?: 0
                            val focus = last.getLong("estFocusLevel")?.toInt() ?: 100
                            val dateObj = last.getTimestamp("startTime")?.toDate()
                            val dateString = if (dateObj != null) 
                                java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(dateObj)
                                else "Recent Session"

                            activity?.runOnUiThread {
                                updateUIComponents(view, score, alerts, duration, dateString, snapshots.size(), 0.0) 
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
            startActivity(Intent(requireContext(), SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
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
            startActivity(Intent(requireContext(), SessionHistoryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
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
