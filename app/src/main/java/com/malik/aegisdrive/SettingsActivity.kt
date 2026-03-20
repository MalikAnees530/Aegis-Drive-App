package com.malik.aegisdrive

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Back button
        findViewById<MaterialCardView>(R.id.btnBack)
            .setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Edit profile button
        findViewById<MaterialCardView>(R.id.editBtn)
            .setOnClickListener {
                Toast.makeText(this, "Edit profile — coming soon", Toast.LENGTH_SHORT).show()
            }

        // Switches
        findViewById<SwitchMaterial>(R.id.switchAlertSound)
            .setOnCheckedChangeListener { _, on ->
                Toast.makeText(this, "Alert Sound ${if (on) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

        findViewById<SwitchMaterial>(R.id.switchVibration)
            .setOnCheckedChangeListener { _, on ->
                Toast.makeText(this, "Vibration ${if (on) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

        findViewById<SwitchMaterial>(R.id.switchVoiceAlerts)
            .setOnCheckedChangeListener { _, on ->
                Toast.makeText(this, "Voice Alerts ${if (on) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

        findViewById<SwitchMaterial>(R.id.switchDarkMode)
            .setOnCheckedChangeListener { _, on ->
                Toast.makeText(this, "Dark Mode ${if (on) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

        // Log Out
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }
}