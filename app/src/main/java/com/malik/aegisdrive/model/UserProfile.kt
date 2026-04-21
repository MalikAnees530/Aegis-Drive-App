package com.malik.aegisdrive.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String = "",
    val accountCreatedAt: Timestamp = Timestamp.now(),
    val lastLoginAt: Timestamp = Timestamp.now(),
    val lifetimeStats: LifetimeStats = LifetimeStats(),
    val preferences: AppPreferences = AppPreferences()
)

@IgnoreExtraProperties
data class LifetimeStats(
    val totalDrives: Long = 0,
    val totalDriveTimeSeconds: Long = 0,
    val averageSafetyScore: Double = 100.0
)

@IgnoreExtraProperties
data class AppPreferences(
    val darkModeEnabled: Boolean = true,
    val audibleAlertsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true
)
