package com.malik.aegisdrive

data class DriveSession(
    val sessionId: String = "",
    val driverUid: String = "",
    val startTime: Long = 0,
    val endTime: Long = 0,
    val durationSeconds: Int = 0,
    val averageSafetyScore: Int = 0,
    val alertCount: Int = 0,
    val maxDrowsinessLevel: String = "None"
)