package com.example.tightbudget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivityLoginBinding
import com.example.tightbudget.firebase.FirebaseUserManager
import kotlinx.coroutines.launch
import java.util.Locale

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val TAG = "LoginActivity"
    private var isRememberMeChecked = false

    // Firebase User Manager
    private lateinit var firebaseUserManager: FirebaseUserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase User Manager
        firebaseUserManager = FirebaseUserManager.getInstance()

        // Set up click listeners
        setupClickListeners()

        // Log for debugging
        Log.d(TAG, "LoginActivity created with Firebase integration")
    }

    private fun setupClickListeners() {

        // Custom checkbox click handler
        binding.checkboxView.setOnClickListener {
            toggleCheckbox()
        }

        // Login button click
        binding.loginButton.setOnClickListener {
            performLogin()
        }

        // Sign up text click
        binding.signUpText.setOnClickListener {
            Log.d(TAG, "Sign up clicked")
            // Navigate to sign up activity
            Intent(this, SignupActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        // Forgot password click
        binding.forgotPassword.setOnClickListener {
            Log.d(TAG, "Forgot password clicked")
            // TODO: Implement forgot password functionality
            Toast.makeText(this, "Forgot password functionality coming soon", Toast.LENGTH_SHORT).show()
        }

        // Social login buttons
        binding.googleLoginButton.setOnClickListener {
            Log.d(TAG, "Google login clicked")
            Toast.makeText(this, "Google login coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.facebookLoginButton.setOnClickListener {
            Log.d(TAG, "Facebook login clicked")
            Toast.makeText(this, "Facebook login coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.appleLoginButton.setOnClickListener {
            Log.d(TAG, "Apple login clicked")
            Toast.makeText(this, "Apple login coming soon", Toast.LENGTH_SHORT).show()
        }

        /// Guest login
        binding.guestLoginText.setOnClickListener {
            Log.d(TAG, "Continue as guest clicked")
            // Navigate to dashboard activity
            Intent(this, DashboardActivity::class.java).also {
                startActivity(it)
                finish() // Optional: Close the current activity
            }
        }
    }

    // Function to toggle the checkbox state
    private fun toggleCheckbox() {
        isRememberMeChecked = !isRememberMeChecked
        updateCheckboxAppearance()
    }

    // Function to update the checkbox appearance based on its state
    private fun updateCheckboxAppearance() {
        if (isRememberMeChecked) {
            binding.checkboxView.setBackgroundResource(R.drawable.custom_checkbox_checked)
        } else {
            binding.checkboxView.setBackgroundResource(R.drawable.custom_checkbox)
        }
    }

    // Function to perform login action
    private fun performLogin() {
        val email = binding.emailInput.text.toString().trim().toLowerCase(Locale.ROOT)
        val password = binding.passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        binding.loginButton.isEnabled = false
        binding.loginButton.text = "Logging in..."

        lifecycleScope.launch {
            try {
                // Authenticate user with Firebase
                val authenticatedUser = firebaseUserManager.authenticateUser(email, password)

                runOnUiThread {
                    // Reset button state
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = "LOGIN"

                    if (authenticatedUser != null) {
                        // Successful login
                        Log.d(TAG, "User authenticated successfully: ${authenticatedUser.email}")

                        // Save user session
                        saveUserSession(authenticatedUser.id)

                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()

                        // Navigate to dashboard
                        val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                        intent.putExtra("USER_EMAIL", email)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)

                    } else {
                        // Authentication failed
                        Log.d(TAG, "Authentication failed for email: $email")
                        Toast.makeText(
                            this@LoginActivity,
                            "Invalid email or password. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during login: ${e.message}", e)

                runOnUiThread {
                    // Reset button state
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = "LOGIN"

                    // Show appropriate error message
                    val errorMessage = when {
                        e.message?.contains("network") == true ->
                            "Network error. Please check your connection and try again"
                        else -> "Login failed. Please try again"
                    }

                    Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // This method is called to save the user session
    private fun saveUserSession(userId: Int) {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putInt("current_user_id", userId)
            putBoolean("is_logged_in", true)
            if (isRememberMeChecked) {
                putBoolean("remember_me", true)
            }
            apply()
        }
        Log.d(TAG, "Saved user session with ID: $userId")
    }
}

