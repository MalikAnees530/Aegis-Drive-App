package com.malik.aegisdrive

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SignUpActivity : AppCompatActivity() {

    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Input Layouts (for error display)
        val nameLayout = findViewById<TextInputLayout>(R.id.nameInputLayout)
        val emailLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        val phoneLayout = findViewById<TextInputLayout>(R.id.phoneInputLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordLayout = findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)

        // Edit Texts
        val etFullName = findViewById<TextInputEditText>(R.id.etFullName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val btnSignUp = findViewById<MaterialButton>(R.id.btnSignUp)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)
        val btnBack = findViewById<MaterialCardView>(R.id.btnBackToLogin)

        btnSignUp.setOnClickListener {
            // Reset all errors
            nameLayout.error = null
            emailLayout.error = null
            phoneLayout.error = null
            passwordLayout.error = null
            confirmPasswordLayout.error = null

            val name = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            var isValid = true

            // Validations
            if (name.isEmpty()) {
                nameLayout.error = "Full name is required"
                isValid = false
            }

            if (email.isEmpty()) {
                emailLayout.error = "Email is required"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Please enter a valid email"
                isValid = false
            }

            if (password.isEmpty()) {
                passwordLayout.error = "Password is required"
                isValid = false
            } else if (password.length < 8) {
                passwordLayout.error = "Password must be at least 8 characters"
                isValid = false
            }

            if (confirmPassword != password) {
                confirmPasswordLayout.error = "Passwords do not match"
                isValid = false
            }

            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (isValid) {
                hideKeyboard()
                showLoadingDialog("Creating your Aegis account...")
                
                // Simulate network delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadingDialog?.dismiss()
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }, 1500)
            }
        }

        tvGoToLogin.setOnClickListener { finish() }
        btnBack.setOnClickListener { finish() }
    }

    private fun showLoadingDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
        dialogView.findViewById<TextView>(R.id.loadingText).text = message
        
        builder.setView(dialogView)
        builder.setCancelable(false)
        
        loadingDialog = builder.create()
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