package com.example.tightbudget

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tightbudget.adapters.DailyChallengesAdapter
import com.example.tightbudget.databinding.ActivityDailyChallengesBinding
import com.example.tightbudget.firebase.GamificationManager
import com.example.tightbudget.models.DailyChallenge
import kotlinx.coroutines.launch

/**
 * Activity to display and manage daily challenges
 */
class DailyChallengesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDailyChallengesBinding
    private lateinit var gamificationManager: GamificationManager
    private lateinit var challengesAdapter: DailyChallengesAdapter
    private var userId: Int = -1

    companion object {
        private const val TAG = "DailyChallengesActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyChallengesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gamificationManager = GamificationManager.getInstance()
        userId = getCurrentUserId()

        setupUI()
        setupRecyclerView()
        loadDailyChallenges()
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.refreshChallengesButton.setOnClickListener {
            refreshChallenges()
        }

        // Set up daily refresh info
        binding.refreshInfoText.text = "Challenges refresh daily at midnight"
    }

    private fun setupRecyclerView() {
        challengesAdapter = DailyChallengesAdapter(emptyList()) { challenge ->
            onChallengeClicked(challenge)
        }

        binding.challengesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DailyChallengesActivity)
            adapter = challengesAdapter
        }
    }

    private fun loadDailyChallenges() {
        if (userId == -1) {
            showGuestState()
            return
        }

        binding.loadingProgressBar.visibility = View.VISIBLE
        binding.challengesRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Check if we need to generate new challenges
                val existingChallenges = gamificationManager.getUserDailyChallenges(userId)
                val todaysChallenges = existingChallenges.filter {
                    isToday(it.dateAssigned) && it.expiresAt > System.currentTimeMillis()
                }

                val challenges = if (todaysChallenges.isEmpty()) {
                    // Generate new challenges for today
                    gamificationManager.generateDailyChallenges(userId)
                } else {
                    todaysChallenges
                }

                runOnUiThread {
                    binding.loadingProgressBar.visibility = View.GONE
                    binding.challengesRecyclerView.visibility = View.VISIBLE

                    if (challenges.isEmpty()) {
                        showEmptyState()
                    } else {
                        challengesAdapter.updateChallenges(challenges)
                        updateChallengesHeader(challenges)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading daily challenges: ${e.message}", e)
                runOnUiThread {
                    binding.loadingProgressBar.visibility = View.GONE
                    showErrorState()
                }
            }
        }
    }

    private fun refreshChallenges() {
        if (userId == -1) {
            Toast.makeText(this, "Please log in to refresh challenges", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val newChallenges = gamificationManager.generateDailyChallenges(userId)

                runOnUiThread {
                    challengesAdapter.updateChallenges(newChallenges)
                    updateChallengesHeader(newChallenges)
                    Toast.makeText(this@DailyChallengesActivity, "Challenges refreshed!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing challenges: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@DailyChallengesActivity, "Failed to refresh challenges", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onChallengeClicked(challenge: DailyChallenge) {
        if (challenge.isCompleted) {
            Toast.makeText(this, "Challenge already completed! 🎉", Toast.LENGTH_SHORT).show()
        } else {
            // Show challenge details or navigate to relevant screen
            val timeLeft = (challenge.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60)
            Toast.makeText(this, "You have ${timeLeft}h left to complete this challenge", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateChallengesHeader(challenges: List<DailyChallenge>) {
        val completedCount = challenges.count { it.isCompleted }
        val totalCount = challenges.size
        val totalPoints = challenges.filter { it.isCompleted }.sumOf { it.pointsReward }

        binding.challengesStatusText.text = "Completed: $completedCount/$totalCount"
        binding.pointsEarnedText.text = "$totalPoints pts earned today"

        // Update progress bar
        val progress = if (totalCount > 0) (completedCount * 100) / totalCount else 0
        binding.challengesProgressBar.progress = progress
    }

    private fun showGuestState() {
        binding.loadingProgressBar.visibility = View.GONE
        binding.challengesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "Log in to access daily challenges and earn points!"
        binding.emptyStateSubtext.text = "Create an account to start your gamification journey"
    }

    private fun showEmptyState() {
        binding.challengesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "No challenges available"
        binding.emptyStateSubtext.text = "Check back tomorrow for new challenges!"
    }

    private fun showErrorState() {
        binding.challengesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "Failed to load challenges"
        binding.emptyStateSubtext.text = "Please check your connection and try again"
    }

    private fun isToday(timestamp: Long): Boolean {
        val today = java.util.Calendar.getInstance()
        val challengeDate = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

        return today.get(java.util.Calendar.YEAR) == challengeDate.get(java.util.Calendar.YEAR) &&
                today.get(java.util.Calendar.DAY_OF_YEAR) == challengeDate.get(java.util.Calendar.DAY_OF_YEAR)
    }
}