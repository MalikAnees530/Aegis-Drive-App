package com.malik.aegisdrive

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnSignUp = findViewById<MaterialButton>(R.id.tvGoToSignUp)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        btnLogin.setOnClickListener {
            emailLayout.error = null
            passwordLayout.error = null

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            var isValid = true
            if (email.isEmpty()) {
                emailLayout.error = "Email is required"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Invalid email format"
                isValid = false
            }

            if (password.isEmpty()) {
                passwordLayout.error = "Password is required"
                isValid = false
            }

            if (isValid) {
                hideKeyboard()
                showLoadingDialog("Authenticating with Aegis...")
                
                // Simulate network delay for a professional feel
                Handler(Looper.getMainLooper()).postDelayed({
                    loadingDialog?.dismiss()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }, 1500)
            }
        }

        btnSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            // Placeholder for forgot password
        }
    }

    private fun showLoadingDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
        dialogView.findViewById<TextView>(R.id.loadingText).text = message
        
        builder.setView(dialogView)
        builder.setCancelable(false)
        
        loadingDialog = builder.create()
        // This makes the dialog background transparent so our rounded corners show perfectly
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}