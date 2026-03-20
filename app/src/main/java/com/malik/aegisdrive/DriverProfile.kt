package com.malik.aegisdrive

data class DriverProfile(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val vehicleType: String = "Sedan", // Default
    val emergencyContact: String = "",
    val joinDate: Long = System.currentTimeMillis(),
    val averageSafetyScore: Int = 100,
    val totalDrives: Int = 0
)