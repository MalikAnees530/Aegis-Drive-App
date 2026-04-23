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
import com.google.firebase.firestore.ListenerRegistration
import java.io.File
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val TAG = "SettingsActivity"
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var profileListener: ListenerRegistration? = null
    private var prefListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🚀 CRITICAL FIX: BREAK INFINITE RECREATION LOOP
        try {
            val settingsPrefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
            val isDarkMode = settingsPrefs.getBoolean("dark_mode", true)
            val targetMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply dark mode", e)
        }

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
        syncPreferences()
    }

    private fun syncPreferences() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)

        // 🚀 READ: Hydrate switches from Cloud Preferences map
        prefListener?.remove()
        prefListener = userRef.addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val prefs = doc.get("preferences") as? Map<*, *>
                
                runOnUiThread {
                    findViewById<SwitchMaterial>(R.id.switchDarkMode)?.apply {
                        val isEnabled = prefs?.get("darkModeEnabled") as? Boolean ?: true
                        if (isChecked != isEnabled) isChecked = isEnabled
                    }
                    findViewById<SwitchMaterial>(R.id.switchAlertSound)?.apply {
                        val isEnabled = prefs?.get("audibleAlertsEnabled") as? Boolean ?: true
                        if (isChecked != isEnabled) isChecked = isEnabled
                    }
                    findViewById<SwitchMaterial>(R.id.switchVibration)?.apply {
                        val isEnabled = prefs?.get("hapticFeedbackEnabled") as? Boolean ?: true
                        if (isChecked != isEnabled) isChecked = isEnabled
                    }
                }
            }
        }
    }

    private fun setupDeleteAccount() {
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

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data?.toMutableMap() ?: mutableMapOf<String, Any>()
                    data["deletedAt"] = FieldValue.serverTimestamp()
                    data["originalUid"] = uid

                    db.collection("DeletedUserRecord").add(data)
                        .addOnSuccessListener {
                            db.collection("users").document(uid).delete()
                                .addOnSuccessListener {
                                    user.delete().addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            getSharedPreferences("AegisProfile", Context.MODE_PRIVATE).edit().clear().apply()
                                            getSharedPreferences("AegisData", Context.MODE_PRIVATE).edit().clear().apply()
                                            startActivity(Intent(this, LoginActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            })
                                            finishAffinity()
                                        }
                                    }
                                }
                        }
                }
            }
    }

    private fun loadUserData() {
        try {
            val currentUser = auth.currentUser ?: return
            findViewById<TextView>(R.id.tvProfileEmail)?.text = currentUser.email ?: "No Email"

            profileListener?.remove()
            profileListener = db.collection("users").document(currentUser.uid)
                .addSnapshotListener { document, e ->
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e)
                        return@addSnapshotListener
                    }
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: document.getString("displayName") ?: "Operator"
                        findViewById<TextView>(R.id.tvProfileName)?.text = name

                        // 🚀 MASTER FIX: READ STATS DIRECTLY FROM ROOT
                        val totalDrives = document.getLong("totalDrives") ?: 0L
                        val totalDuration = document.getLong("totalDuration") ?: 0L
                        val scoreSum = document.getLong("lifetimeScoreSum") ?: 0L

                        val safetyPercent = if (totalDrives > 0) (scoreSum.toDouble() / totalDrives) else 100.0
                        
                        // 🚀 DYNAMIC TIME FORMATTING (H M S)
                        val h = totalDuration / 3600
                        val m = (totalDuration % 3600) / 60
                        val s = totalDuration % 60

                        val driveTimeText = when {
                            h > 0 -> String.format(java.util.Locale.US, "%dh %dm %ds", h, m, s)
                            m > 0 -> String.format(java.util.Locale.US, "%dm %ds", m, s)
                            else -> String.format(java.util.Locale.US, "%ds", s)
                        }

                        runOnUiThread {
                            findViewById<TextView>(R.id.tvStatTrips)?.text = totalDrives.toString()
                            findViewById<TextView>(R.id.tvStatDriveTime)?.text = driveTimeText
                            findViewById<TextView>(R.id.tvStatSafety)?.text = String.format(Locale.US, "%.1f%%", safetyPercent)
                        }
                    }
                }

            // 🚀 LOAD SAVED AVATAR FROM LOCAL STORAGE
            try {
                val profilePrefs = getSharedPreferences("AegisProfile", Context.MODE_PRIVATE)
                val imagePath = profilePrefs.getString("user_image_path", null)
                if (imagePath != null) {
                    val uri = Uri.parse(imagePath)
                    val path = uri.path // Robustly extract path from URI string
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            val ivAvatar = findViewById<ImageView>(R.id.ivProfileAvatar)
                            ivAvatar?.setImageURI(Uri.fromFile(file))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load avatar", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile load failed", e)
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.editBtn)?.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val prefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)

        findViewById<SwitchMaterial>(R.id.switchAlertSound)?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                userRef.update("preferences.audibleAlertsEnabled", isChecked)
                prefs.edit().putBoolean("alert_sound", isChecked).apply()
            }
        }

        findViewById<SwitchMaterial>(R.id.switchVibration)?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                userRef.update("preferences.hapticFeedbackEnabled", isChecked)
                prefs.edit().putBoolean("vibration", isChecked).apply()
            }
        }

        findViewById<SwitchMaterial>(R.id.switchDarkMode)?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                try {
                    userRef.update("preferences.darkModeEnabled", isChecked)
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()
                    val targetMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                        AppCompatDelegate.setDefaultNightMode(targetMode)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Theme switch failed", e)
                }
            }
        }

        findViewById<View>(R.id.rowAbout)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Aegis Drive Intelligence")
                .setMessage("Aegis Drive is an offline-first digital guardian that uses Edge AI to detect driver fatigue in real-time. It combines proactive safety alerts with premium navigation to ensure a secure journey.")
                .setPositiveButton("CLOSE", null)
                .show()
        }

        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Terminate Session?")
                .setPositiveButton("LOGOUT") { _, _ ->
                    auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finishAffinity()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileListener?.remove()
        prefListener?.remove()
    }
}
