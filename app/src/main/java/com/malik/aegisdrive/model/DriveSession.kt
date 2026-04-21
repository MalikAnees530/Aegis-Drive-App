package com.malik.aegisdrive.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class DriveSession(
    @get:Exclude var id: String = "",
    val sessionNumber: Int = 0,
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp = Timestamp.now(),
    val durationSeconds: Long = 0,
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
    val maxEyeClosureDurationMs: Long = 0
)
