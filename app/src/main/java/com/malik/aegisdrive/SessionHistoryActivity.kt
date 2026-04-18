package com.malik.aegisdrive

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@SuppressLint("SetTextI18n")
class SessionHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        val btnBack = findViewById<View>(R.id.btnBack)
        val listContainer = findViewById<LinearLayout>(R.id.listContainer)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnBack?.setOnClickListener { finish() }

        // 🚀 FETCH DATA FROM FIREBASE
        progressBar?.visibility = View.VISIBLE
        val db = FirebaseFirestore.getInstance()
        db.collection("DriveSessions")
            .orderBy("dateObject", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar?.visibility = View.GONE
                if (documents.isEmpty) {
                    showEmptyState(listContainer)
                } else {
                    listContainer?.removeAllViews()
                    for (doc in documents) {
                        addSessionCard(listContainer!!, doc)
                    }
                }
            }
            .addOnFailureListener {
                progressBar?.visibility = View.GONE
                Toast.makeText(this, "Cloud Sync Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addSessionCard(container: LinearLayout, doc: com.google.firebase.firestore.DocumentSnapshot) {
        val sessionNum = doc.get("sessionNumber") ?: "?"
        val score = doc.getLong("score")?.toInt() ?: 0
        val timestamp = doc.getString("timestamp") ?: "Unknown Date"
        val alerts = doc.getLong("alerts")?.toInt() ?: 0
        val duration = doc.getLong("duration")?.toInt() ?: 0

        // Create Modern Card
        val card = MaterialCardView(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 12, 0, 12)
            layoutParams = params
            radius = 40f
            cardElevation = 0f
            strokeWidth = 2
            setStrokeColor(Color.parseColor("#333333"))
            setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            isClickable = true
            isFocusable = true
        }

        // Inner Content Layout
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER_VERTICAL
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(this).apply {
            text = "Session #$sessionNum"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }

        val date = TextView(this).apply {
            text = timestamp
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
        }

        textLayout.addView(title)
        textLayout.addView(date)

        val scoreValue = TextView(this).apply {
            text = "$score%"
            setTextColor(getScoreColor(score))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        }

        row.addView(textLayout)
        row.addView(scoreValue)
        card.addView(row)

        // 🚀 DETAIL VIEW ON CLICK
        card.setOnClickListener {
            showSessionDetails(sessionNum, timestamp, score, alerts, duration)
        }

        container.addView(card)
    }

    private fun showSessionDetails(num: Any, date: String, score: Int, alerts: Int, duration: Int) {
        val hours = duration / 3600
        val mins = (duration % 3600) / 60
        val secs = duration % 60
        val timeFormatted = if (hours > 0) "${hours}h ${mins}m ${secs}s" else "${mins}m ${secs}s"

        val level = when {
            score >= 90 -> "Elite"
            score >= 80 -> "Professional"
            score >= 70 -> "Expert"
            score >= 60 -> "Standard"
            score >= 50 -> "Moderate"
            else -> "Critical"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("SESSION ANALYSIS #$num")
            .setMessage("""
                📅 DATE: $date
                ⏱ DURATION: $timeFormatted
                ⚠ ALERTS FIRED: $alerts
                🎯 SAFETY SCORE: $score%
                🏆 STATUS: $level Level
            """.trimIndent())
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showEmptyState(container: LinearLayout?) {
        val tv = TextView(this).apply {
            text = "No driving logs found."
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }
        container?.addView(tv)
    }

    private fun getScoreColor(score: Int): Int {
        return when {
            score > 75 -> Color.parseColor("#6ABF69")
            score > 45 -> Color.parseColor("#FFB74D")
            else -> Color.parseColor("#EF5350")
        }
    }
}