package com.example.tightbudget

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivitySignupBinding
import com.example.tightbudget.firebase.FirebaseUserManager
import com.example.tightbudget.models.User
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private val TAG = "SignupActivity"

    // Add a variable to track checkbox state
    private var isTermsChecked = false

    // Firebase User Manager
    private lateinit var firebaseUserManager: FirebaseUserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase User Manager
        firebaseUserManager = FirebaseUserManager.getInstance()

        // Initialise password indicator strength indicator to an empty state
        binding.passwordStrengthText.text = ""
        binding.passwordStrengthBar.setBackgroundColor(getColor(android.R.color.transparent))

        // Set up click listeners
        setupClickListeners()

        // Set up text change listeners
        setupTextWatchers()

        // Log for debugging
        Log.d(TAG, "SignupActivity created with Firebase integration")
    }

    private fun setupClickListeners() {
        // Terms checkbox click handling
        binding.termsCheckboxView.setOnClickListener {
            toggleTermsCheckbox()
        }

        // Create account button click
        binding.createAccountButton.setOnClickListener {
            createAccount()
        }

        // Login text click
        binding.loginText.setOnClickListener {
            Log.d(TAG, "Login clicked")
            // Navigate to login activity
            Intent(this, LoginActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        // Terms and privacy policy clicks
        binding.termsText.setOnClickListener {
            Log.d(TAG, "Terms clicked")
            // Open terms and conditions page
            // TODO: Implement terms and conditions functionality/screen
            Toast.makeText(this, "Terms and Conditions", Toast.LENGTH_SHORT).show()
        }

        binding.privacyPolicyText.setOnClickListener {
            Log.d(TAG, "Privacy Policy clicked")
            // Open privacy policy page
            Toast.makeText(this, "Privacy Policy", Toast.LENGTH_SHORT).show()
        }

        // Social signup buttons
        binding.googleLoginButton.setOnClickListener {
            Log.d(TAG, "Google signup clicked")
            Toast.makeText(this, "Google signup clicked", Toast.LENGTH_SHORT).show()
        }

        binding.facebookLoginButton.setOnClickListener {
            Log.d(TAG, "Facebook signup clicked")
            Toast.makeText(this, "Facebook signup clicked", Toast.LENGTH_SHORT).show()
        }

        binding.appleLoginButton.setOnClickListener {
            Log.d(TAG, "Apple signup clicked")
            Toast.makeText(this, "Apple signup clicked", Toast.LENGTH_SHORT).show()
        }

        // Guest login
        binding.guestLoginText.setOnClickListener {
            Log.d(TAG, "Continue as guest clicked")
            // Navigate to dashboard activity without login
            Intent(this, DashboardActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }
    }

    private fun setupTextWatchers() {
        // Password strength check
        binding.passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                updatePasswordStrength(s.toString())
                checkPasswordsMatch()  // Also check matching when main password changes
            }
        })

        // Password matching check
        binding.confirmPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                checkPasswordsMatch()
            }
        })
    }

    private fun updatePasswordStrength(password: String) {
        // Simple password strength algorithm. It checks for:
        // 1. Length (at least 6 characters)
        // 2. Contains at least one digit
        // 3. Contains at least one special character

        when {
            password.isEmpty() -> {
                // Handle empty password case
                binding.passwordStrengthText.text = ""
                binding.passwordStrengthBar.setBackgroundColor(getColor(android.R.color.transparent))
            }

            password.length < 6 -> {
                binding.passwordStrengthText.text = "Weak - too short"
                binding.passwordStrengthText.setTextColor(getColor(R.color.red_light))
                binding.passwordStrengthBar.setBackgroundColor(getColor(R.color.red_light))
            }

            !password.any { it.isDigit() } -> {
                binding.passwordStrengthText.text = "Medium - add numbers"
                binding.passwordStrengthText.setTextColor(getColor(R.color.teal_light))
                binding.passwordStrengthBar.setBackgroundColor(getColor(R.color.teal_light))
            }

            !password.any { !it.isLetterOrDigit() } -> {
                binding.passwordStrengthText.text = "Medium - add special characters"
                binding.passwordStrengthText.setTextColor(getColor(R.color.teal_light))
                binding.passwordStrengthBar.setBackgroundColor(getColor(R.color.teal_light))
            }

            else -> {
                binding.passwordStrengthText.text = "Strong password"
                binding.passwordStrengthText.setTextColor(getColor(R.color.green_light))
                binding.passwordStrengthBar.setBackgroundColor(getColor(R.color.green_light))
            }
        }
    }

    // Function to check if the passwords match
    private fun checkPasswordsMatch() {
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()

        // Only show the message if the confirm password field is not empty
        if (confirmPassword.isNotEmpty()) {
            if (password == confirmPassword) {
                // Passwords match - hide warning
                binding.passwordMatchWarning.visibility = View.GONE
            } else {
                // Passwords don't match - show warning
                binding.passwordMatchWarning.visibility = View.VISIBLE
            }
        } else {
            // Confirm password is empty - hide warning
            binding.passwordMatchWarning.visibility = View.GONE
        }
    }

    // Function to toggle terms checkbox state
    private fun toggleTermsCheckbox() {
        isTermsChecked = !isTermsChecked
        updateTermsCheckboxAppearance()
    }

    // Update the checkbox appearance based on current state
    private fun updateTermsCheckboxAppearance() {
        if (isTermsChecked) {
            binding.termsCheckboxView.setBackgroundResource(R.drawable.custom_checkbox_checked)
        } else {
            binding.termsCheckboxView.setBackgroundResource(R.drawable.custom_checkbox)
        }
    }

    private fun createAccount() {
        val fullName = binding.fullNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()

        // Validation checks
        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
            return
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty() || password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isTermsChecked) {
            Toast.makeText(this, "Please agree to the Terms and Privacy Policy", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Show loading state
        binding.createAccountButton.isEnabled = false
        binding.createAccountButton.text = "Creating Account..."

        // Create user using Firebase
        val newUser = User(
            fullName = fullName,
            email = email,
            password = password,
            balance = 0.0 // Default balance
        )

        lifecycleScope.launch {
            try {
                // Create user in Firebase
                val createdUser = firebaseUserManager.createUser(newUser)

                Log.d(TAG, "User created successfully in Firebase with ID: ${createdUser.id}")

                runOnUiThread {
                    Toast.makeText(
                        this@SignupActivity,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to success screen
                    val intent = Intent(this@SignupActivity, SuccessActivity::class.java)
                    intent.putExtra("USER_EMAIL", email)
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error creating account: ${e.message}", e)

                runOnUiThread {
                    // Reset button state
                    binding.createAccountButton.isEnabled = true
                    binding.createAccountButton.text = "CREATE ACCOUNT"

                    // Show appropriate error message
                    val errorMessage = when {
                        e.message?.contains("already exists") == true ->
                            "An account with this email already exists"
                        e.message?.contains("network") == true ->
                            "Network error. Please check your connection and try again"
                        else -> "Error creating account. Please try again"
                    }

                    Toast.makeText(this@SignupActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}