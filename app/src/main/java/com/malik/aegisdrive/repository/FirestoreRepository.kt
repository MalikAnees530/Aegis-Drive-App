package com.malik.aegisdrive.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.malik.aegisdrive.model.*
import java.text.SimpleDateFormat
import java.util.*

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "AegisRepository"

    /**
     * Creates the initial user profile and logs the security event.
     */
    fun createUserProfile(uid: String, user: UserProfile) {
        val userRef = db.collection("users").document(uid)
        val auditRef = db.collection("security_audit").document()

        db.runBatch { batch ->
            batch.set(userRef, user)
            batch.set(auditRef, SecurityLog("NEW_SIGNUP", uid))
        }.addOnSuccessListener {
            Log.d(TAG, "User profile and signup audit created for: $uid")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to create user profile", e)
        }
    }

    /**
     * Updates nested preferences map within the user document.
     */
    fun updateUserPreferences(uid: String, prefs: AppPreferences) {
        db.collection("users").document(uid)
            .update("preferences", prefs)
            .addOnSuccessListener { Log.d(TAG, "Preferences updated for: $uid") }
            .addOnFailureListener { e -> Log.e(TAG, "Pref update failed", e) }
    }

    /**
     * Saves session to subcollection and updates parent lifetimeStats atomically.
     */
    fun saveDriveSession(uid: String, session: DriveSession) {
        val userRef = db.collection("users").document(uid)
        val sessionRef = userRef.collection("drive_sessions").document()
        
        // Dynamic ID for global analytics
        val dateId = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
        val analyticsRef = db.collection("system_analytics").document("daily_stats_$dateId")

        db.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            
            // Extract stats from nested map safely
            val lifetimeStats = userSnapshot.get("lifetimeStats") as? Map<*, *>
            val prevTotalDrives = (lifetimeStats?.get("totalDrives") as? Long) ?: 0L
            val prevAvg = (lifetimeStats?.get("averageSafetyScore") as? Double) ?: 100.0
            
            // Calculate new weighted average
            val newAvg = ((prevAvg * prevTotalDrives) + session.finalSafetyScore) / (prevTotalDrives + 1)

            // 1. Write the Drive Session to subcollection
            transaction.set(sessionRef, session)

            // 2. Update User Lifetime Stats (Parent Document)
            transaction.update(userRef, mapOf(
                "lifetimeStats.totalDrives" to FieldValue.increment(1),
                "lifetimeStats.totalDriveTimeSeconds" to FieldValue.increment(session.durationSeconds),
                "lifetimeStats.averageSafetyScore" to newAvg,
                "lastLoginAt" to com.google.firebase.Timestamp.now()
            ))

            // 3. Update Global Health Analytics (Atomic Merge)
            transaction.set(analyticsRef, mapOf(
                "totalSessionsLoggedToday" to FieldValue.increment(1),
                "criticalAlertsTriggeredGlobally" to FieldValue.increment(if (session.finalSafetyScore < 50) 1 else 0)
            ), SetOptions.merge())

        }.addOnSuccessListener {
            Log.d(TAG, "Atomic Session Save and Analytics Update Success")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Transaction failed: ", e)
        }
    }
}
