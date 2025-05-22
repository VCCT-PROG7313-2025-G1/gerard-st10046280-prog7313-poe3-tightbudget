package com.example.tightbudget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.example.tightbudget.databinding.ActivitySettingsBinding

/**
 * SettingsActivity provides the user interface for app settings and preferences.
 * It handles user preferences such as appearance settings, notifications,
 * account management, and app information.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val TAG = "SettingsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup the UI components and event listeners
        setupBackButton()
        setupBottomNavigation()
        setupAccountSettings()
        setupNotificationSettings()
        setupAppearanceSettings()
        setupSupportSettings()
        setupAppInfoSettings()
        setupSignOutButton()
    }

    /**
     * Handles the back button navigation
     */
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * Sets up the bottom navigation bar with correct selection
     */
    private fun setupBottomNavigation() {
        val bottomNavBar = binding.bottomNavBar
        bottomNavBar.selectedItemId = R.id.nav_settings

        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }

                R.id.nav_reports -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }

                R.id.nav_add_transaction -> {
                    startActivity(Intent(this, AddTransactionActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }

                R.id.nav_wallet -> {
                    startActivity(Intent(this, TransactionsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }

                R.id.nav_settings -> true // Already on this screen
                else -> false
            }
        }
    }

    /**
     * Sets up the account settings section
     */
    private fun setupAccountSettings() {
        // Edit Profile
        binding.editProfile.setOnClickListener {
            Log.d(TAG, "Edit Profile clicked")
            Toast.makeText(
                this,
                "Profile editing will be available in the next update",
                Toast.LENGTH_SHORT
            ).show()

            // In a full implementation, this would navigate to a profile edit screen
            // startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // Change Password
        binding.changePassword.setOnClickListener {
            Log.d(TAG, "Change Password clicked")
            showChangePasswordDialog()
        }
    }

    /**
     * Shows a dialog for changing the password
     */
    private fun showChangePasswordDialog() {
        // This is a placeholder dialog. In a real app, this would include proper password
        // validation and storage
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Sets up the notification settings section
     */
    private fun setupNotificationSettings() {
        val sharedPrefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val reminderEnabled = sharedPrefs.getBoolean("goal_reminders_enabled", true)

        // Update the UI to reflect current settings
        binding.goalRemindersSwitch.isChecked = reminderEnabled

        binding.goalRemindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Goal Reminders toggled: $isChecked")

            // Save the user preference
            sharedPrefs.edit().putBoolean("goal_reminders_enabled", isChecked).apply()

            // Show confirmation
            val message = if (isChecked) "Goal reminders enabled" else "Goal reminders disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sets up the appearance settings section
     */
    private fun setupAppearanceSettings() {
        val sharedPrefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val darkModeEnabled = sharedPrefs.getBoolean("dark_mode_enabled", false)

        // Update the UI to reflect current settings
        binding.darkModeSwitch.isChecked = darkModeEnabled

        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Dark Mode toggled: $isChecked")

            // Save the user preference
            sharedPrefs.edit().putBoolean("dark_mode_enabled", isChecked).apply()

            // Apply the theme change
            val nightMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            AppCompatDelegate.setDefaultNightMode(nightMode)

            // Note: The activity will be recreated automatically when the night mode changes
        }
    }

    /**
     * Sets up the support settings section
     */
    private fun setupSupportSettings() {
        // Help Center
        binding.help.setOnClickListener {
            Log.d(TAG, "Help Center clicked")

            // In a full implementation, this would navigate to a help center screen or open a web view with help documentation
            Toast.makeText(this, "Help Center coming soon", Toast.LENGTH_SHORT).show()
        }

        // Send Feedback
        binding.feedback.setOnClickListener {
            Log.d(TAG, "Send Feedback clicked")
            showFeedbackDialog()
        }
    }

    /**
     * Shows a dialog for sending feedback
     */
    private fun showFeedbackDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_feedback, null)

        AlertDialog.Builder(this)
            .setTitle("Send Feedback")
            .setView(dialogView)
            .setPositiveButton("Send") { dialog, _ ->
                Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Sets up the app info settings section
     */
    private fun setupAppInfoSettings() {
        // App Version - Set the current version
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.version.text = "📱 App Version $versionName"

        // Terms & Conditions
        binding.terms.setOnClickListener {
            Log.d(TAG, "Terms & Conditions clicked")

            // In a full implementation, this would display terms in a WebView
            // or open a terms page
            Toast.makeText(this, "Terms & Conditions will be displayed here", Toast.LENGTH_SHORT)
                .show()
        }

        // Privacy Policy
        binding.privacy.setOnClickListener {
            Log.d(TAG, "Privacy Policy clicked")

            // In a full implementation, this would display privacy policy in a WebView or open a privacy policy page
            Toast.makeText(this, "Privacy Policy will be displayed here", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sets up the sign out button
     */
    private fun setupSignOutButton() {
        binding.signOut.setOnClickListener {
            Log.d(TAG, "Sign Out clicked")
            showSignOutConfirmationDialog()
        }
    }

    /**
     * Shows a confirmation dialog before signing out
     */
    private fun showSignOutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                performSignOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Performs the sign out operation
     */
    private fun performSignOut() {
        // Clear user session
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            remove("current_user_id")
            remove("is_logged_in")
            apply()
        }

        Log.d(TAG, "User signed out successfully")

        // Navigate to login screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}