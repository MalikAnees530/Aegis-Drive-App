package com.malik.aegisdrive.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class DriveSession(
    @get:com.google.firebase.firestore.Exclude var id: String = "", // Document ID Excluded from Firestore Map
    val sessionNumber: Int = 0,
    val startTime: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val endTime: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val durationSeconds: Long = 0L,
    val finalSafetyScore: Int = 0,
    val estFocusLevel: Int = 0,
    val totalAlertsFired: Int = 0,
    val aiEngineMetrics: AiEngineMetrics = AiEngineMetrics(),
    val appVersionAtTime: String = "v1.0"
)

@IgnoreExtraProperties
data class AiEngineMetrics(
    val drowsyEventsDetected: Int = 0,
    val yawningEventsDetected: Int = 0,
    val maxEyeClosureDurationMs: Long = 0L
)
