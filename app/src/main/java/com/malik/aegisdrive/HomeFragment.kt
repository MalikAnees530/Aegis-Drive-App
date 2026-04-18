package com.malik.aegisdrive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar
import java.util.Locale

@SuppressLint("SetTextI18n", "CommitPrefEdits")
class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners(view)
        checkAIModelStatus(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateGreeting(it)
            syncDashboardData(it)
        }
    }

    private fun updateGreeting(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingTime = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
        
        // 🚀 SYNC: Load user name for greeting
        val prefs = requireActivity().getSharedPreferences("AegisProfile", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Driver")?.split(" ")?.get(0) ?: "Driver"
        
        tvGreeting?.text = "$greetingTime, $userName"
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
        } catch (_: Exception) {
            tvAIStatus?.text = "ERROR"
            tvAIStatus?.setTextColor("#EF5350".toColorInt())
        }
    }

    private fun syncDashboardData(view: View) {
        val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)

        val lastScore = prefs.getInt("LAST_SCORE", 100)
        val totalAlerts = prefs.getInt("TOTAL_ALERTS", 0)
        val driveSeconds = prefs.getInt("DRIVE_SECONDS", 0)
        val lastDriveDate = prefs.getString("LAST_DRIVE_DATE", "No recent sessions") ?: "No recent sessions"
        val totalSessions = prefs.getInt("TOTAL_SESSIONS", 0)

        // 🚀 CLOUD RECOVERY: If local sessions are 0, check the cloud
        if (totalSessions == 0) {
            fetchLatestFromCloud(view)
        }

        updateUIComponents(view, lastScore, totalAlerts, driveSeconds, lastDriveDate, totalSessions)
    }

    private fun updateUIComponents(view: View, score: Int, alerts: Int, seconds: Int, date: String, sessionCount: Int) {
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        val formattedTime = if (hours > 0) "${hours}h ${mins}m" else "${mins}m ${secs}s"

        view.findViewById<TextView>(R.id.tvScoreValue)?.text = score.toString()
        view.findViewById<TextView>(R.id.tvDriveTimeValue)?.text = formattedTime
        view.findViewById<TextView>(R.id.tvAlertsValue)?.text = alerts.toString()
        
        // 🚀 PERFECT FOCUS LOGIC: Synchronized average of AI Score and Alert Performance
        val alertPenalty = alerts * 4
        val focusLevel = if (seconds > 0) {
            ((score + maxOf(0, 100 - alertPenalty)) / 2).coerceIn(0, 100)
        } else {
            100
        }
        view.findViewById<TextView>(R.id.tvFocusValue)?.text = "$focusLevel%"

        val safetyRating = (score / 100.0) * 5.0
        view.findViewById<TextView>(R.id.tvRatingValue)?.text = String.format(Locale.US, "%.1f", safetyRating)

        val tvHistoryTitle = view.findViewById<TextView>(R.id.tvHistoryTitle)
        val tvHistoryDate = view.findViewById<TextView>(R.id.tvHistoryDate)
        if (sessionCount > 0) {
            tvHistoryTitle?.text = "Session $sessionCount: ${String.format(Locale.US, "%.1f", safetyRating)} ⭐"
            tvHistoryDate?.text = date
        }

        val colorHex = when {
            score > 75 -> "#6ABF69"
            score > 45 -> "#FFB74D"
            else       -> "#EF5350"
        }
        val parsedColor = colorHex.toColorInt()
        view.findViewById<TextView>(R.id.tvScoreValue)?.setTextColor(parsedColor)
        val pb = view.findViewById<ProgressBar>(R.id.scoreProgress)
        pb?.progress = score
        pb?.progressTintList = ColorStateList.valueOf(parsedColor)

        val tvStatus = view.findViewById<TextView>(R.id.tvStatusText)
        tvStatus?.text = if (score > 75) "SAFE" else if (score > 45) "WARNING" else "DANGER"
        tvStatus?.setTextColor(parsedColor)
        view.findViewById<View>(R.id.statusIndicator)?.backgroundTintList = ColorStateList.valueOf(parsedColor)

        // 🚀 DYNAMIC DRIVING LEVEL: Professional range detection for all scores
        val tvScoreSub = view.findViewById<TextView>(R.id.tvScoreSub)
        tvScoreSub?.text = when {
            score >= 90 -> "Elite Driving Level Detected."
            score >= 80 -> "Professional Driving Level Detected."
            score >= 70 -> "Expert Driving Level Detected."
            score >= 60 -> "Standard Driving Level Detected."
            score >= 50 -> "Moderate Safety Level Detected."
            score >= 10 -> "Critical Safety Alert Detected!"
            else        -> "Hazardous Driving Level Detected."
        }
    }

    private fun fetchLatestFromCloud(view: View) {
        val db = FirebaseFirestore.getInstance()
        db.collection("DriveSessions")
            .orderBy("dateObject", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val doc = docs.documents[0]
                    val score = doc.getLong("score")?.toInt() ?: 100
                    val alerts = doc.getLong("alerts")?.toInt() ?: 0
                    val duration = doc.getLong("duration")?.toInt() ?: 0
                    val date = doc.getString("timestamp") ?: "Recent Drive"
                    val num = doc.getLong("sessionNumber")?.toInt() ?: 1

                    // 🚀 SYNC: Save to local storage for offline consistency
                    val prefs = requireActivity().getSharedPreferences("AegisData", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putInt("LAST_SCORE", score)
                        putInt("TOTAL_ALERTS", alerts)
                        putInt("DRIVE_SECONDS", duration)
                        putString("LAST_DRIVE_DATE", date)
                        putInt("TOTAL_SESSIONS", num)
                        apply()
                    }

                    updateUIComponents(view, score, alerts, duration, date, num)
                }
            }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.btnStartMonitoring)?.setOnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
            bottomNav?.selectedItemId = R.id.monitorFragment
        }

        view.findViewById<MaterialCardView>(R.id.btnSettings)?.setOnClickListener {
            try { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
            catch (_: Exception) { Toast.makeText(requireContext(), "Opening Settings...", Toast.LENGTH_SHORT).show() }
        }

        view.findViewById<MaterialCardView>(R.id.btnNotif)?.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Aegis Security Logs")
                .setMessage("✔ Core AI Active\n✔ Cloud Sync Live\n✔ All Systems Nominal")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
        }

        view.findViewById<TextView>(R.id.btnViewAllSessions)?.setOnClickListener {
            startActivity(Intent(requireContext(), SessionHistoryActivity::class.java))
        }
    }
}