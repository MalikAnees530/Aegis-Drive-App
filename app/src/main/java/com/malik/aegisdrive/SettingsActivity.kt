package com.malik.aegisdrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.io.File
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val TAG = "SettingsActivity"
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🚀 SYNC: Ensure theme is applied before layout inflation
        val settingsPrefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
        val isDarkMode = settingsPrefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        setupListeners()
        setupDeleteAccount()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        syncStatsFromCloud()
    }

    private var statsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun syncStatsFromCloud() {
        try {
            val currentUser = auth.currentUser ?: return
            
            // PHASE 1: STRICT ISOLATION - Filter by userId
            statsListener?.remove()
            statsListener = db.collection("DriveSessions")
                .whereEqualTo("userId", currentUser.uid)
                .addSnapshotListener { documents, _ ->
                    if (documents != null) {
                        var totalSecs = 0
                        var totalScore = 0
                        var count = 0

                        for (doc in documents) {
                            val duration = doc.getLong("duration")?.toInt() ?: 0
                            val score = doc.getLong("score")?.toInt() ?: 0
                            
                            totalSecs += duration
                            totalScore += score
                            count++
                        }

                        val dataPrefs = getSharedPreferences("AegisData", Context.MODE_PRIVATE)
                        dataPrefs.edit().apply {
                            putInt("TOTAL_DRIVE_TIME", totalSecs)
                            putInt("TOTAL_SCORE_SUM", totalScore)
                            putInt("TOTAL_SESSIONS", count)
                            apply()
                        }
                        updateStatsUI()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Stats sync failed", e)
        }
    }

    private var profileListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun loadUserData() {
        try {
            val currentUser = auth.currentUser ?: return
            
            // Set email immediately from Auth
            findViewById<TextView>(R.id.tvProfileEmail)?.text = currentUser.email ?: "No Email"

            // 🚀 REAL-TIME SYNC: Listen for profile changes (Lowercase 'users')
            profileListener?.remove()
            profileListener = db.collection("users").document(currentUser.uid)
                .addSnapshotListener { document, e ->
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e)
                        return@addSnapshotListener
                    }
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: "Operator"
                        findViewById<TextView>(R.id.tvProfileName)?.text = name
                    }
                }

            // Load Local Avatar if exists
            val profilePrefs = getSharedPreferences("AegisProfile", Context.MODE_PRIVATE)
            val imagePath = profilePrefs.getString("user_image_path", null)
            val ivAvatar = findViewById<ImageView>(R.id.ivProfileAvatar)
            if (imagePath != null && ivAvatar != null) {
                val file = File(imagePath)
                if (file.exists()) {
                    ivAvatar.setImageURI(Uri.fromFile(file))
                }
            }

            updateStatsUI()
            
        } catch (e: Exception) {
            Log.e(TAG, "Profile load failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileListener?.remove()
    }

    private fun updateStatsUI() {
        try {
            val dataPrefs = getSharedPreferences("AegisData", Context.MODE_PRIVATE)
            val totalDriveSeconds = dataPrefs.getInt("TOTAL_DRIVE_TIME", 0)
            val totalScoreSum = dataPrefs.getInt("TOTAL_SCORE_SUM", 0)
            val totalSessions = dataPrefs.getInt("TOTAL_SESSIONS", 0)

            val avgSafetyPercent = if (totalSessions > 0) {
                totalScoreSum.toDouble() / totalSessions 
            } else {
                100.0
            }

            val totalHours = totalDriveSeconds / 3600
            val totalMinutes = (totalDriveSeconds % 3600) / 60
            val totalSecs = totalDriveSeconds % 60
            
            val driveTimeText = when {
                totalHours > 0 -> String.format(Locale.US, "%dh %dm", totalHours, totalMinutes)
                totalMinutes > 0 -> String.format(Locale.US, "%dm %ds", totalMinutes, totalSecs)
                else -> String.format(Locale.US, "%ds", totalSecs)
            }
            
            findViewById<TextView>(R.id.tvStatDriveTime)?.text = driveTimeText
            findViewById<TextView>(R.id.tvStatSafety)?.text = String.format(Locale.US, "%.1f%%", avgSafetyPercent)
            findViewById<TextView>(R.id.tvStatTrips)?.text = totalSessions.toString()
        } catch (e: Exception) { }
    }

    private fun setupDeleteAccount() {
        // Long press on logo or a hidden area could trigger this, or add a button in XML
        // For now, I'll attach it to the logout button's long click for security testing
        findViewById<View>(R.id.btnLogout)?.setOnLongClickListener {
            showDeleteAccountDialog()
            true
        }
    }

    private fun showDeleteAccountDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("PERMANENT DELETION")
            .setMessage("This will archive your data and terminate your identity. This cannot be undone.")
            .setPositiveButton("DELETE EVERYTHING") { _, _ ->
                performSoftDelete()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun performSoftDelete() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        // PHASE 2: SOFT-DELETE ARCHITECTURE
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data ?: return@addOnSuccessListener
                    data["deletedAt"] = FieldValue.serverTimestamp()
                    data["originalUid"] = uid

                    // Step 2: Archive to DeletedUserRecord
                    db.collection("DeletedUserRecord").add(data)
                        .addOnSuccessListener {
                            // Step 3: Delete from active users
                            db.collection("users").document(uid).delete()
                                .addOnSuccessListener {
                                    // Step 4: Delete Auth Credentials
                                    user.delete().addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            // Cleanup local storage
                                            getSharedPreferences("AegisProfile", Context.MODE_PRIVATE).edit().clear().apply()
                                            getSharedPreferences("AegisData", Context.MODE_PRIVATE).edit().clear().apply()
                                            
                                            val intent = Intent(this, LoginActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            finishAffinity()
                                        }
                                    }
                                }
                        }
                }
            }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<View>(R.id.editBtn)?.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

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
                try {
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()
                    AppCompatDelegate.setDefaultNightMode(
                        if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Theme switch failed", e)
                }
            }
        }

        findViewById<View>(R.id.rowAbout)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Aegis Drive Intelligence")
                .setMessage("Enterprise Security Framework v1.0.0")
                .setPositiveButton("CLOSE", null)
                .show()
        }

        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Terminate Session?")
                .setPositiveButton("LOGOUT") { _, _ ->
                    auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finishAffinity()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }
}
