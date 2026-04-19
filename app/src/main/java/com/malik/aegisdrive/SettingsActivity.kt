package com.malik.aegisdrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🚀 SYNC: Ensure theme is applied before layout inflation
        val settingsPrefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
        val isDarkMode = settingsPrefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        try {
            // 🚀 SYNC: Load user profile
            val profilePrefs = getSharedPreferences("AegisProfile", Context.MODE_PRIVATE)
            val userName = profilePrefs.getString("user_name", "Driver Name")
            val imagePath = profilePrefs.getString("user_image_path", null)

            findViewById<TextView>(R.id.tvProfileName)?.text = userName
            findViewById<TextView>(R.id.tvProfileEmail)?.text = "driver@aegisdrive.com"

            val ivAvatar = findViewById<ImageView>(R.id.ivProfileAvatar)
            if (imagePath != null && ivAvatar != null) {
                val file = File(imagePath)
                if (file.exists()) {
                    ivAvatar.setImageURI(Uri.fromFile(file))
                }
            }

            // 🚀 SYNC: Load real-time stats from AegisData
            val dataPrefs = getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            
            // Cumulative Logic
            val totalDriveSeconds = dataPrefs.getInt("TOTAL_DRIVE_TIME", 0)
            val totalScoreSum = dataPrefs.getInt("TOTAL_SCORE_SUM", 0)
            val totalSessions = dataPrefs.getInt("TOTAL_SESSIONS", 0)

            // 🚀 SENIOR FIX: Cumulative Safety % (0-100)
            val avgSafetyPercent = if (totalSessions > 0) {
                totalScoreSum.toDouble() / totalSessions 
            } else {
                100.0 // Default to 100% for a clean professional start
            }

            // High-Precision Cumulative Time Formatting
            val totalHours = totalDriveSeconds / 3600
            val totalMinutes = (totalDriveSeconds % 3600) / 60
            val totalSecs = totalDriveSeconds % 60
            
            val driveTimeText = when {
                totalHours > 0 -> String.format(Locale.US, "%dh %dm %ds", totalHours, totalMinutes, totalSecs)
                totalMinutes > 0 -> String.format(Locale.US, "%dm %ds", totalMinutes, totalSecs)
                else -> String.format(Locale.US, "%ds", totalSecs)
            }
            
            findViewById<TextView>(R.id.tvStatDriveTime)?.text = driveTimeText
            findViewById<TextView>(R.id.tvStatSafety)?.text = String.format(Locale.US, "%.1f%%", avgSafetyPercent)
            findViewById<TextView>(R.id.tvStatTrips)?.text = totalSessions.toString()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        // Safe Back Navigation
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        // Edit Profile Navigation
        findViewById<View>(R.id.editBtn)?.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // Toggles with persistence logic
        val prefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)

        findViewById<SwitchMaterial>(R.id.switchAlertSound)?.apply {
            isChecked = prefs.getBoolean("alert_sound", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("alert_sound", isChecked).apply()
            }
        }

        findViewById<SwitchMaterial>(R.id.switchVibration)?.apply {
            isChecked = prefs.getBoolean("vibration", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("vibration", isChecked).apply()
            }
        }

        findViewById<SwitchMaterial>(R.id.switchDarkMode)?.apply {
            isChecked = prefs.getBoolean("dark_mode", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("dark_mode", isChecked).apply()
                // Update theme instantly
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }

        // About Row (Long click to reset for developers/senior test)
        findViewById<View>(R.id.rowAbout)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Aegis Drive Intelligence")
                .setMessage("Version 1.0.0 (Production)\n\nDeveloped for high-stakes driver safety monitoring using real-time Computer Vision and LSTM.\n\nWould you like to reset your driving statistics?")
                .setPositiveButton("CLOSE", null)
                .setNeutralButton("RESET STATS") { _, _ ->
                    getSharedPreferences("AegisData", Context.MODE_PRIVATE).edit().clear().apply()
                    loadUserData()
                    Toast.makeText(this, "Statistics Reset Successfully", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Logout
        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Terminate Session?")
                .setMessage("Log out of the Aegis Security Network?")
                .setPositiveButton("LOGOUT") { _, _ ->
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }
}