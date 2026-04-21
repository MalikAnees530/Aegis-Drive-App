package com.malik.aegisdrive.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.malik.aegisdrive.model.*

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    fun createUserProfile(uid: String, email: String, name: String) {
        val user = UserProfile(email = email, displayName = name)
        db.collection("users").document(uid).set(user)
            .addOnSuccessListener { Log.d("AegisRepo", "Profile Created") }
            .addOnFailureListener { Log.e("AegisRepo", "Signup Sync Failed", it) }
    }

    fun saveDriveSession(uid: String, session: DriveSession) {
        val userRef = db.collection("users").document(uid)
        val sessionRef = userRef.collection("drive_sessions").document()

        db.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            
            // Safe extraction from nested map
            val lifetimeStats = userSnapshot.get("lifetimeStats") as? Map<*, *>
            val currentDrives = (lifetimeStats?.get("totalDrives") as? Long) ?: 0L
            val currentAvg = (lifetimeStats?.get("averageSafetyScore") as? Double) ?: 100.0
            
            // Recalculate Average Safety Score weighted by total drives
            val newAvg = ((currentAvg * currentDrives) + session.finalSafetyScore) / (currentDrives + 1)

            // Atomic Updates: Write session and update parent stats simultaneously
            transaction.set(sessionRef, session)
            transaction.update(userRef, mapOf(
                "lifetimeStats.totalDrives" to FieldValue.increment(1),
                "lifetimeStats.totalDriveTimeSeconds" to FieldValue.increment(session.durationSeconds),
                "lifetimeStats.averageSafetyScore" to newAvg,
                "lastLoginAt" to FieldValue.serverTimestamp()
            ))
        }.addOnSuccessListener { Log.d("AegisRepo", "Session & Lifetime Stats Synced") }
         .addOnFailureListener { Log.e("AegisRepo", "Transaction Failed", it) }
    }
}
