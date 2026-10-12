package com.ironparadise.gymcrm.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.UserEntity
import com.ironparadise.gymcrm.databinding.ActivityLoginBinding
import com.ironparadise.gymcrm.ui.MainActivity
import com.ironparadise.gymcrm.util.AppConfig
import com.ironparadise.gymcrm.data.network.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val app by lazy { IronParadiseApp.instance }

    private var isSignUpMode = false
    private val isStaffApp get() = AppConfig.isStaffApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Auto-clear old demo email sessions if present
        val savedEmail = app.sessionManager.userEmail.lowercase()
        if (savedEmail.contains("demo") || savedEmail.contains("trainer.sam") || savedEmail.contains("staff.rohit")) {
            app.sessionManager.clearSession()
        }

        // Check if already logged in and approved
        if (app.sessionManager.isLoggedIn) {
            if (app.sessionManager.isApproved) {
                navigateToMain()
                return
            } else {
                showPendingApprovalState(app.sessionManager.userEmail)
            }
        }

        setupPortalTheme()
        setupAuthToggle()
        setupActionListeners()
    }

    private fun setupPortalTheme() {
        if (isStaffApp) {
            binding.tvPortalBadge.text = "STAFF PORTAL • FRONT DESK"
            binding.tvPortalBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.tvPortalBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)
            binding.tvAuthSubtitle.text = "Staff billing, stock status & client checkout"
        } else {
            binding.tvPortalBadge.text = "ADMIN PORTAL • FULL CONTROL"
            binding.tvPortalBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
            binding.tvPortalBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
            binding.tvAuthSubtitle.text = "Manage inventory costs, discounts, and staff approvals"
        }
    }

    private fun setupAuthToggle() {
        binding.btnTabSignIn.setOnClickListener {
            if (isSignUpMode) {
                isSignUpMode = false
                updateAuthModeUI()
            }
        }

        binding.btnTabSignUp.setOnClickListener {
            if (!isSignUpMode) {
                isSignUpMode = true
                updateAuthModeUI()
            }
        }
        updateAuthModeUI()
    }

    private fun updateAuthModeUI() {
        if (isSignUpMode) {
            binding.btnTabSignUp.setBackgroundResource(R.drawable.bg_badge_low_stock)
            binding.btnTabSignUp.setTextColor(ContextCompat.getColor(this, R.color.bg_black))
            binding.btnTabSignIn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnTabSignIn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            binding.tilName.visibility = View.VISIBLE
            binding.btnAuthAction.text = if (isStaffApp) "CREATE STAFF ACCOUNT" else "CREATE ADMIN ACCOUNT"
        } else {
            binding.btnTabSignIn.setBackgroundResource(R.drawable.bg_badge_low_stock)
            binding.btnTabSignIn.setTextColor(ContextCompat.getColor(this, R.color.bg_black))
            binding.btnTabSignUp.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnTabSignUp.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            binding.tilName.visibility = View.GONE
            binding.btnAuthAction.text = "SIGN IN TO CRM"
        }
    }

    private fun setupActionListeners() {
        binding.btnAuthAction.setOnClickListener {
            if (isSignUpMode) {
                handleSignUp()
            } else {
                handleSignIn()
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            handleGoogleSignIn()
        }

        binding.btnSwitchAccount.setOnClickListener {
            app.sessionManager.clearSession()
            binding.cardPendingApproval.visibility = View.GONE
            binding.etEmail.setText("")
            binding.etPassword.setText("")
            binding.etName.setText("")
            Toast.makeText(this, "Session cleared. You can now sign in or register with another account.", Toast.LENGTH_SHORT).show()
        }

        binding.btnRefreshApproval.setOnClickListener {
            val email = binding.etEmail.text.toString().trim().ifBlank { app.sessionManager.userEmail }
            if (email.isBlank()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkApprovalStatus(email)
        }

        binding.btnQuickVerifyCode.setOnClickListener {
            promptAdminVerificationPin()
        }
    }

    private fun handleSignIn() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.tilEmail.error = "Please enter email"
            return
        }
        binding.tilEmail.error = null

        if (password.isEmpty()) {
            binding.tilPassword.error = "Please enter password or PIN"
            return
        }
        binding.tilPassword.error = null

        lifecycleScope.launch(Dispatchers.IO) {
            val user = app.repository.getUserByCredentials(email, password)
            withContext(Dispatchers.Main) {
                if (user != null) {
                    if (user.status == "PENDING") {
                        app.sessionManager.saveUserSession(user)
                        showPendingApprovalState(user.email)
                    } else if (user.status == "REJECTED") {
                        Toast.makeText(this@LoginActivity, "Account has been rejected by Admin", Toast.LENGTH_LONG).show()
                    } else {
                        // Success
                        if (binding.cbRememberMe.isChecked) {
                            app.sessionManager.saveUserSession(user)
                        }
                        Toast.makeText(this@LoginActivity, "Welcome back, ${user.name}!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                } else {
                    binding.tilPassword.error = "Invalid email or password/PIN"
                }
            }
        }
    }

    private fun handleSignUp() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilName.error = "Please enter your full name"
            return
        }
        binding.tilName.error = null

        if (email.isEmpty() || !email.contains("@")) {
            binding.tilEmail.error = "Please enter a valid email address"
            return
        }
        binding.tilEmail.error = null

        if (password.length < 4) {
            binding.tilPassword.error = "Password must be at least 4 characters"
            return
        }
        binding.tilPassword.error = null

        lifecycleScope.launch(Dispatchers.IO) {
            val existing = app.repository.getUserByEmail(email)
            if (existing != null) {
                withContext(Dispatchers.Main) {
                    binding.tilEmail.error = "An account with this email already exists"
                }
                return@launch
            }

            val role = if (isStaffApp) "STAFF" else "ADMIN"
            val status = if (isStaffApp) "PENDING" else "APPROVED"

            val newUser = UserEntity(
                email = email,
                name = name,
                password = password,
                pin = if (password.length == 4 && password.all { it.isDigit() }) password else "",
                role = role,
                status = status,
                authProvider = "EMAIL"
            )

            val id = app.repository.registerUser(newUser)
            val createdUser = newUser.copy(id = id)

            // Asynchronously sync with cloud server in background
            CloudSyncManager.registerRemoteUser(this@LoginActivity, createdUser)

            withContext(Dispatchers.Main) {
                if (binding.cbRememberMe.isChecked) {
                    app.sessionManager.saveUserSession(createdUser)
                }

                if (status == "PENDING") {
                    showPendingApprovalState(email)
                    Toast.makeText(
                        this@LoginActivity,
                        "Staff account created! Awaiting Admin approval.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Admin account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                }
            }
        }
    }

    private fun handleGoogleSignIn() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 0)
        }

        val etGoogleName = EditText(this).apply {
            hint = "Full Name (e.g. John Doe)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val etGoogleEmail = EditText(this).apply {
            hint = "Google Email (e.g. yourname@gmail.com)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        container.addView(etGoogleName)
        container.addView(etGoogleEmail)

        AlertDialog.Builder(this)
            .setTitle("1-Tap Google Sign-In")
            .setMessage("Enter your Google account details to sign in or create your profile:")
            .setView(container)
            .setPositiveButton("Sign In with Google") { _, _ ->
                val email = etGoogleEmail.text.toString().trim().lowercase()
                val nameInput = etGoogleName.text.toString().trim()
                val name = if (nameInput.isNotBlank()) nameInput else email.substringBefore("@").replace(".", " ").capitalizeWords()

                if (email.contains("@") && email.contains(".")) {
                    completeGoogleAuth(email, name)
                } else {
                    Toast.makeText(this, "Please enter a valid Google email address", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun completeGoogleAuth(email: String, name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            var user = app.repository.getUserByEmail(email)
            if (user == null) {
                val role = if (isStaffApp) "STAFF" else "ADMIN"
                val status = if (isStaffApp) "PENDING" else "APPROVED"
                val newUser = UserEntity(
                    email = email,
                    name = name,
                    password = "google_auth_${System.currentTimeMillis()}",
                    role = role,
                    status = status,
                    authProvider = "GOOGLE"
                )
                val id = app.repository.registerUser(newUser)
                user = newUser.copy(id = id)
            }

            withContext(Dispatchers.Main) {
                app.sessionManager.saveUserSession(user)
                if (user.status == "PENDING") {
                    showPendingApprovalState(email)
                    Toast.makeText(
                        this@LoginActivity,
                        "Google Staff Account created! Pending Admin approval.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Google Sign-In successful: $name", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                }
            }
        }
    }

    private fun showPendingApprovalState(email: String) {
        binding.cardPendingApproval.visibility = View.VISIBLE
        binding.tvPendingDesc.text = "Account ($email) is currently awaiting approval from the Gym Admin. Once approved, you can log in."
    }

    private fun checkApprovalStatus(email: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            var user = app.repository.getUserByEmail(email)

            // Check live 24/7 cloud server for real-time admin approval
            val remoteStatus = CloudSyncManager.checkRemoteUserStatus(this@LoginActivity, email)
            if (remoteStatus != null && user != null && remoteStatus != user.status) {
                app.repository.updateStaffStatus(user.id, remoteStatus)
                user = user.copy(status = remoteStatus)
            }

            withContext(Dispatchers.Main) {
                if (user != null) {
                    if (user.status == "APPROVED") {
                        app.sessionManager.saveUserSession(user)
                        Toast.makeText(this@LoginActivity, "Account Approved! Logging in...", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else if (user.status == "REJECTED") {
                        Toast.makeText(this@LoginActivity, "Account registration has been rejected by Admin.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@LoginActivity, "Still pending approval from Admin.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Account not found.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptAdminVerificationPin() {
        val input = EditText(this).apply {
            hint = "4-digit Admin Code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Enter Admin Approval Code")
            .setMessage("Ask your Gym Admin to enter their master PIN to instantly verify and approve this device:")
            .setView(input)
            .setPositiveButton("Verify & Activate") { _, _ ->
                val code = input.text.toString().trim()
                if (code == AppConfig.MASTER_ADMIN_APPROVAL_CODE) {
                    val email = binding.etEmail.text.toString().trim().ifBlank { app.sessionManager.userEmail }
                    lifecycleScope.launch(Dispatchers.IO) {
                        val user = app.repository.getUserByEmail(email)
                        if (user != null) {
                            app.repository.updateStaffStatus(user.id, "APPROVED")
                            app.sessionManager.saveUserSession(user.copy(status = "APPROVED"))
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@LoginActivity, "Staff Account verified and activated by Admin!", Toast.LENGTH_SHORT).show()
                                navigateToMain()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Incorrect Admin Code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
