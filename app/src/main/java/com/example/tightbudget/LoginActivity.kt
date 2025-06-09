package com.example.tightbudget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivityLoginBinding
import com.example.tightbudget.firebase.FirebaseCategoryManager
import com.example.tightbudget.firebase.FirebaseUserManager
import com.example.tightbudget.utils.CategoryMigrationHelper
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
        Log.d(TAG, "LoginActivity created with Firebase integration and user-specific categories")
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
            // Clear any existing user session to ensure guest mode
            clearUserSession()
            // Navigate to dashboard activity in guest mode
            Intent(this, DashboardActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }
    }

    /**
     * Clear user session to ensure guest mode
     * This method ensures guest users don't see previous user's data
     */
    private fun clearUserSession() {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            remove("current_user_id")
            remove("is_logged_in")
            remove("remember_me")
            apply()
        }
        Log.d(TAG, "Cleared user session for guest mode")
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

                        // Handle successful login with category setup
                        onLoginSuccess(authenticatedUser.id, email)

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

    /**
     * Handle successful login - includes category migration/seeding
     */
    private fun onLoginSuccess(userId: Int, email: String) {
        // Save user session
        saveUserSession(userId)

        // Show success message
        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()

        // Ensure user has categories (migration + seeding) in background
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Setting up categories for user: $userId")
                val hasCategories = CategoryMigrationHelper.ensureUserHasCategories(userId)
                if (hasCategories) {
                    Log.d(TAG, "User $userId has categories ready")
                } else {
                    Log.w(TAG, "Failed to ensure categories for user $userId")
                    // Still proceed to dashboard - categories will be created on-demand if needed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up categories for user $userId: ${e.message}", e)
                // Don't show error to user - this is a background operation
            }

            // Navigate to dashboard regardless of category setup result
            runOnUiThread {
                val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                intent.putExtra("USER_EMAIL", email)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
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

    /**
     * Seeds default categories for a user after successful login (DEPRECATED)
     * Use CategoryMigrationHelper.ensureUserHasCategories() instead
     */
    @Deprecated("Use CategoryMigrationHelper.ensureUserHasCategories() instead")
    private fun seedUserCategoriesAfterLogin(userId: Int) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Seeding default categories for user: $userId")
                val firebaseCategoryManager = FirebaseCategoryManager.getInstance()
                firebaseCategoryManager.seedDefaultCategoriesForUser(userId)
                Log.d(TAG, "Successfully seeded categories for user: $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error seeding categories for user $userId: ${e.message}", e)
            }
        }
    }
}