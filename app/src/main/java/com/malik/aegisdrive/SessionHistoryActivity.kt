package com.malik.aegisdrive

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@SuppressLint("SetTextI18n")
class SessionHistoryActivity : AppCompatActivity() {

    private var historyListener: com.google.firebase.firestore.ListenerRegistration? = null
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var progressBar: ProgressBar
    private val sessionList = mutableListOf<DriveSession>()
    private lateinit var adapter: SessionHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        rvHistory = findViewById(R.id.rvHistory)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        progressBar = findViewById(R.id.progressBar)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.btnWipeHistory)?.setOnClickListener { confirmWipeHistory() }

        adapter = SessionHistoryAdapter(sessionList)
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        startHistorySync()
    }

    override fun onResume() {
        super.onResume()
        applyProgrammaticTheme()
    }

    private fun applyProgrammaticTheme() {
        val prefs = getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)

        val root = findViewById<View>(R.id.historyRoot)
        val tvHeader = findViewById<TextView>(R.id.tvHistoryHeader)
        val btnBackCard = findViewById<MaterialCardView>(R.id.btnBack)
        val tvBackArrow = findViewById<TextView>(R.id.tvBackArrow)

        if (isDarkMode) {
            root?.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            tvHeader?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            btnBackCard?.setCardBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
            btnBackCard?.setStrokeColor(android.graphics.Color.parseColor("#3C4043"))
            tvBackArrow?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        } else {
            root?.setBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"))
            tvHeader?.setTextColor(android.graphics.Color.parseColor("#1F1F1F"))
            btnBackCard?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
            btnBackCard?.setStrokeColor(android.graphics.Color.parseColor("#DADCE0"))
            tvBackArrow?.setTextColor(android.graphics.Color.parseColor("#1F1F1F"))
        }
    }

    private fun startHistorySync() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        historyListener?.remove()
        historyListener = FirebaseFirestore.getInstance().collection("DriveSessions")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                progressBar.visibility = View.GONE
                if (e != null) {
                    Log.e("History", "Sync failed", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    sessionList.clear()
                    for (doc in snapshots) {
                        try {
                            val session = DriveSession(
                                id = doc.id,
                                score = doc.getLong("score")?.toInt() ?: 0,
                                duration = doc.getLong("duration")?.toInt() ?: 0,
                                alerts = doc.getLong("alerts")?.toInt() ?: 0,
                                dateString = doc.getString("dateString") ?: "Recent Session"
                            )
                            sessionList.add(session)
                        } catch (ex: Exception) {
                            Log.e("History", "Row error", ex)
                        }
                    }
                    
                    adapter.notifyDataSetChanged()
                    tvEmptyState.visibility = if (sessionList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
    }

    private fun confirmWipeHistory() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Wipe Intelligence History?")
            .setMessage("This will permanently delete all your driving logs. This cannot be undone.")
            .setPositiveButton("WIPE EVERYTHING") { _, _ -> performWipe() }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun performWipe() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("DriveSessions")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                for (doc in snapshots) batch.delete(doc.reference)
                db.collection("SystemAnalytics").document(uid).delete()
                batch.commit().addOnSuccessListener {
                    AegisNotify.show(this, "Intelligence History Wiped", AegisNotify.Type.SUCCESS)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        historyListener?.remove()
    }
}
