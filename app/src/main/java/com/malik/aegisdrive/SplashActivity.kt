package com.malik.aegisdrive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🚀 CRITICAL FIX 1: BREAK INFINITE RECREATION LOOP
        val prefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        val targetMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO

        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }

        // installSplashScreen() MUST be called BEFORE super.onCreate()
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Re-introducing fallback XML layout to prevent native splash hangs
        setContentView(R.layout.activity_splash)

        // Use lifecycleScope for crash-proof threading
        lifecycleScope.launch {
            try {
                // Ensure Firebase is initialized
                FirebaseApp.initializeApp(this@SplashActivity)
                
                // Mandatory 2-second branding delay
                delay(2000)
                
                // Perform routing after the delay
                performSessionCheck()
            } catch (e: Exception) {
                Log.e("SplashError", "Initialization or Coroutine failed", e)
                routeToLogin()
            }
        }
    }

    private fun performSessionCheck() {
        try {
            // Check current user session
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser != null) {
                Log.d(TAG, "Operator session active: ${currentUser.email}")
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
            } else {
                Log.d(TAG, "No active session. Routing to Login.")
                routeToLogin()
            }
            finish()
        } catch (e: Exception) {
            Log.e("SplashError", "Routing failed - Firebase or Context issue", e)
            routeToLogin()
        }
    }

    private fun routeToLogin() {
        try {
            val intent = Intent(this@SplashActivity, LoginActivity::class.java)
            // Clear backstack
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("SplashError", "Fatal: Could not route to LoginActivity", e)
        }
    }
}
