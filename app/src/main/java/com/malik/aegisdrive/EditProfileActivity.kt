package com.malik.aegisdrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var ivAvatarEdit: ImageView
    private lateinit var etName: EditText
    private lateinit var etEmailRead: EditText
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // 🚀 FIXED: Save the image locally immediately to avoid permission crashes
            val internalUri = saveImageToInternalStorage(uri)
            if (internalUri != null) {
                selectedImageUri = internalUri
                ivAvatarEdit.setImageURI(internalUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        ivAvatarEdit = findViewById(R.id.ivAvatarEdit)
        etName = findViewById(R.id.etName)
        etEmailRead = findViewById(R.id.etEmailRead)

        loadCurrentProfile()
        setupListeners()
    }

    private fun loadCurrentProfile() {
        val prefs = getSharedPreferences("AegisProfile", Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "Driver Name")
        val imagePath = prefs.getString("user_image_path", null)

        etName.setText(name)
        
        // 🚀 SYNC: Fetch current operator email
        val currentUser = FirebaseAuth.getInstance().currentUser
        etEmailRead.setText(currentUser?.email ?: "Not Authenticated")

        if (imagePath != null) {
            val file = File(imagePath)
            if (file.exists()) {
                ivAvatarEdit.setImageURI(Uri.fromFile(file))
            }
        }
    }

    private fun setupListeners() {
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialCardView>(R.id.btnChangeAvatar).setOnClickListener { pickImageLauncher.launch("image/*") }
        findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener { saveProfile() }
        findViewById<MaterialButton>(R.id.btnDeleteAccount).setOnClickListener { confirmAccountDeletion() }
    }

    /**
     * 🚀 CRITICAL FIX: Copies the gallery image to the app's private folder.
     * This prevents SecurityException crashes when loading the image later.
     */
    private fun saveImageToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(filesDir, "profile_picture.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveProfile() {
        val newName = etName.text.toString().trim()
        if (newName.isEmpty()) {
            AegisNotify.show(this, "Name cannot be empty", AegisNotify.Type.WARNING)
            return
        }

        val prefs = getSharedPreferences("AegisProfile", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_name", newName)
            if (selectedImageUri != null) {
                // Save the absolute file path, not the content URI
                putString("user_image_path", selectedImageUri?.path)
            }
            apply()
        }

        AegisNotify.show(this, "Profile Saved Successfully", AegisNotify.Type.SUCCESS)
        finish()
    }

    private fun confirmAccountDeletion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("CRITICAL: Delete Account?")
            .setMessage("This action is irreversible. All your data will be permanently erased.\n\nAre you sure?")
            .setPositiveButton("DELETE EVERYTHING") { _, _ -> deleteAccountData() }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun deleteAccountData() {
        val user = FirebaseAuth.getInstance().currentUser
        
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // 🚀 SYNC: Clear Local Data
                getSharedPreferences("AegisProfile", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("AegisData", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("AegisSettings", Context.MODE_PRIVATE).edit().clear().apply()
                val profileFile = File(filesDir, "profile_picture.jpg")
                if (profileFile.exists()) profileFile.delete()

                // Firestore cleanup
                FirebaseFirestore.getInstance().collection("users").document(user.uid).delete()

                AegisNotify.show(this, "Aegis Identity Purged", AegisNotify.Type.SUCCESS)

                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finishAffinity()
            } else {
                val exception = task.exception
                if (exception is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    val snackbar = Snackbar.make(findViewById(android.R.id.content), 
                        "Security: Please logout and login again to delete account.", Snackbar.LENGTH_LONG)
                    snackbar.show()
                } else {
                    AegisNotify.show(this, "Purge Failed: ${exception?.localizedMessage}", AegisNotify.Type.ERROR)
                }
            }
        }
    }
}