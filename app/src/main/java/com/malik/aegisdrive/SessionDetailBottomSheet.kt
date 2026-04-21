package com.malik.aegisdrive

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class SessionDetailBottomSheet(private val sessionId: String) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_session_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyProgrammaticTheme(view)
        view.findViewById<View>(R.id.btnCloseSheet).setOnClickListener { dismiss() }

        FirebaseFirestore.getInstance().collection("DriveSessions").document(sessionId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && isAdded) {
                    val score = doc.getLong("score")?.toInt() ?: 0
                    val duration = doc.getLong("duration")?.toInt() ?: 0
                    val alerts = doc.getLong("alerts")?.toInt() ?: 0
                    val focus = doc.getLong("focusLevel")?.toInt() ?: 0
                    val date = doc.getString("dateString") ?: "Recent Session"

                    view.findViewById<TextView>(R.id.tvSheetScore).text = "$score%"
                    view.findViewById<TextView>(R.id.tvSheetDate).text = date
                    view.findViewById<TextView>(R.id.tvSheetAlerts).text = alerts.toString()
                    view.findViewById<TextView>(R.id.tvSheetFocus).text = "$focus%"

                    val h = duration / 3600
                    val m = (duration % 3600) / 60
                    val s = duration % 60
                    view.findViewById<TextView>(R.id.tvSheetDuration).text = 
                        if (h > 0) String.format(Locale.US, "%dh %dm %ds", h, m, s) else String.format(Locale.US, "%dm %ds", m, s)
                }
            }
    }

    private fun applyProgrammaticTheme(view: View) {
        val prefs = requireContext().getSharedPreferences("AegisSettings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)

        val root = view.findViewById<LinearLayout>(R.id.sheetRoot)
        val tvTitle = view.findViewById<TextView>(R.id.tvSheetTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvSheetDate)

        if (isDarkMode) {
            root?.setBackgroundColor(Color.parseColor("#1E293B")) // Surface Navy
            tvTitle?.setTextColor(Color.parseColor("#FFFFFF"))
            tvDate?.setTextColor(Color.parseColor("#BDBDBD"))
        } else {
            root?.setBackgroundColor(Color.parseColor("#F8F9FA")) // Light Grey
            tvTitle?.setTextColor(Color.parseColor("#1F1F1F"))
            tvDate?.setTextColor(Color.parseColor("#5F6368"))
        }
    }
}
