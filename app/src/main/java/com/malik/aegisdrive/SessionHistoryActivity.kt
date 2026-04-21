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
import com.malik.aegisdrive.model.DriveSession

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
    }

    override fun onStart() {
        super.onStart()
        startHistorySync()
    }

    override fun onStop() {
        super.onStop()
        historyListener?.remove()
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

        val bgColor = if (isDarkMode) "#121212" else "#F8F9FA"
        val txtColor = if (isDarkMode) "#FFFFFF" else "#1F1F1F"
        val cardBg = if (isDarkMode) "#1E1E1E" else "#FFFFFF"
        val strokeColor = if (isDarkMode) "#3C4043" else "#DADCE0"

        root?.setBackgroundColor(android.graphics.Color.parseColor(bgColor))
        tvHeader?.setTextColor(android.graphics.Color.parseColor(txtColor))
        btnBackCard?.setCardBackgroundColor(android.graphics.Color.parseColor(cardBg))
        btnBackCard?.setStrokeColor(android.graphics.Color.parseColor(strokeColor))
        tvBackArrow?.setTextColor(android.graphics.Color.parseColor(txtColor))
    }

    private fun startHistorySync() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        // 🚀 Target the new private subcollection
        historyListener?.remove()
        historyListener = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("drive_sessions")
            .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                progressBar.visibility = View.GONE
                if (e != null) {
                    Log.e("AegisHistory", "Subcollection read failed: ", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    sessionList.clear()
                    // Map documents to new DriveSession model (hierarchical)
                    // We manually iterate to inject the Document ID into the model if needed
                    // For the SessionHistoryAdapter to trigger the BottomSheet, it needs session.id
                    for (doc in snapshots) {
                        try {
                            val session = doc.toObject(DriveSession::class.java)
                            if (session != null) {
                                session.id = doc.id // Inject Document ID
                                sessionList.add(session)
                            }
                        } catch (ex: Exception) {
                            Log.e("History", "Mapping error", ex)
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

        // Hierarchical Wipe: Delete from user subcollection
        db.collection("users").document(uid).collection("drive_sessions").get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                for (doc in snapshots) batch.delete(doc.reference)
                
                // Reset Lifetime Stats map
                val userRef = db.collection("users").document(uid)
                batch.update(userRef, "lifetimeStats", com.malik.aegisdrive.model.LifetimeStats())
                
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
