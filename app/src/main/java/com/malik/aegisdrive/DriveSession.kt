package com.malik.aegisdrive

data class DriveSession(
    val id: String = "",
    val userId: String = "",
    val score: Int = 0,
    val duration: Int = 0,
    val alerts: Int = 0,
    val focusLevel: Int = 0,
    val dateString: String = ""
)
