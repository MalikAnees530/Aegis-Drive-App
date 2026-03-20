package com.malik.aegisdrive

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class HomeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 1. Initialize Greeting
        updateGreeting(view)
        
        // 2. Setup Interactions
        setupClickListeners(view)
        
        // 3. Load Real-Time Dashboard Data
        // In Phase 12, this will come from a ViewModel/AI Engine
        updateDashboardUI(view, score = 82, time = "1h 12m", alerts = 0, focus = 100, rating = 4.9)
    }

    private fun updateGreeting(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        val greetingPrefix = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }

        val user = auth.currentUser
        if (user != null) {
            db.collection("drivers").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (isAdded && document != null && document.exists()) {
                        val fullName = document.getString("fullName") ?: "Driver"
                        val firstName = fullName.split(" ").firstOrNull() ?: "Driver"
                        tvGreeting.text = "$greetingPrefix, $firstName"
                    } else {
                        tvGreeting.text = greetingPrefix
                    }
                }
                .addOnFailureListener {
                    if (isAdded) tvGreeting.text = greetingPrefix
                }
        } else {
            tvGreeting.text = greetingPrefix
        }
    }

    private fun updateDashboardUI(view: View, score: Int, time: String, alerts: Int, focus: Int, rating: Double) {
        // --- Score Section ---
        view.findViewById<TextView>(R.id.tvScoreValue).text = score.toString()
        view.findViewById<TextView>(R.id.tvProgressVal).text = "$score%"
        view.findViewById<ProgressBar>(R.id.scoreProgress).progress = score
        
        val tvScoreSub = view.findViewById<TextView>(R.id.tvScoreSub)
        tvScoreSub.text = if (score > 80) "Elite safety level" else "System operational"

        // --- Metrics Section ---
        view.findViewById<TextView>(R.id.tvDriveTimeValue).text = time
        view.findViewById<ProgressBar>(R.id.pbDriveTime).progress = 65 // Mock progress for bar
        
        view.findViewById<TextView>(R.id.tvAlertsValue).text = alerts.toString()
        view.findViewById<ProgressBar>(R.id.pbAlerts).progress = (alerts * 10).coerceAtMost(100)
        
        view.findViewById<TextView>(R.id.tvFocusValue).text = "$focus%"
        view.findViewById<ProgressBar>(R.id.pbFocus).progress = focus
        
        view.findViewById<TextView>(R.id.tvRatingValue).text = rating.toString()
        view.findViewById<ProgressBar>(R.id.pbRating).progress = (rating * 20).toInt()

        // --- Status Badge ---
        val tvStatus = view.findViewById<TextView>(R.id.tvStatusText)
        val statusIndicator = view.findViewById<View>(R.id.statusIndicator)
        if (score >= 80) {
            tvStatus.text = "SAFE"
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_safe))
            statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_safe))
        }
    }

    private fun setupClickListeners(view: View) {
        // Start Monitoring Button
        view.findViewById<MaterialButton>(R.id.btnStartMonitoring).setOnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
            bottomNav?.selectedItemId = R.id.monitorFragment
        }

        // Settings & Notif
        view.findViewById<MaterialCardView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<MaterialCardView>(R.id.btnNotif).setOnClickListener {
            Toast.makeText(requireContext(), "Aegis System: All shields optimal.", Toast.LENGTH_SHORT).show()
        }

        // Alerts Interactions
        view.findViewById<TextView>(R.id.tvViewAll).setOnClickListener {
            Toast.makeText(requireContext(), "Syncing with Alert History...", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialCardView>(R.id.alert1).setOnClickListener {
            Toast.makeText(requireContext(), "Analyzing Alert Event...", Toast.LENGTH_SHORT).show()
        }
    }
}