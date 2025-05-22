package com.example.tightbudget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import kotlin.apply

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val TAG = "LoginActivity"
    private var isRememberMeChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up click listeners
        setupClickListeners()

        // Log for debugging
        Log.d(TAG, "LoginActivity created")
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
            Toast.makeText(this, "Forgot password clicked", Toast.LENGTH_SHORT).show()
        }

        // Social login buttons
        binding.googleLoginButton.setOnClickListener {
            Log.d(TAG, "Google login clicked")
            Toast.makeText(this, "Google login clicked", Toast.LENGTH_SHORT).show()
        }

        binding.facebookLoginButton.setOnClickListener {
            Log.d(TAG, "Facebook login clicked")
            Toast.makeText(this, "Facebook login clicked", Toast.LENGTH_SHORT).show()
        }

        binding.appleLoginButton.setOnClickListener {
            Log.d(TAG, "Apple login clicked")
            Toast.makeText(this, "Apple login clicked", Toast.LENGTH_SHORT).show()
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
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            return
        }

        val db = AppDatabase.getDatabase(this)
        val userDao = db.userDao()

        lifecycleScope.launch {
            val user = userDao.getUserByEmail(email)

            if (user == null) {
                // User does not exist
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "User does not exist", Toast.LENGTH_SHORT).show()
                }
            } else if (user.password != password) {
                // Incorrect password
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Successful login
                saveUserSession(user.id)

                Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()

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
            apply()
        }
        Log.d(TAG, "Saved user session with ID: $userId")
    }
}

