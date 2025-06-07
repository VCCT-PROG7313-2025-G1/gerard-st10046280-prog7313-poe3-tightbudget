package com.example.tightbudget

import android.content.Context
import android.content.Intent
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
import java.util.Calendar

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

        setupToolbar()
        setupUI()
        setupRecyclerView()
        loadDailyChallenges()
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        // Refresh button click
        binding.refreshButton.setOnClickListener {
            refreshChallenges()
        }

        // Empty state refresh button
        binding.emptyStateRefreshButton.setOnClickListener {
            refreshChallenges()
        }

        // FAB click - quick add transaction
        binding.quickActionFab.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        // Update time remaining
        updateTimeRemaining()
    }

    private fun setupRecyclerView() {
        challengesAdapter = DailyChallengesAdapter(emptyList()) { challenge ->
            onChallengeClicked(challenge)
        }

        binding.challengesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DailyChallengesActivity)
            adapter = challengesAdapter
            // Add item spacing
            addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(
                this@DailyChallengesActivity,
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            ).apply {
                setDrawable(getDrawable(android.R.color.transparent)!!)
            })
        }
    }

    private fun loadDailyChallenges() {
        if (userId == -1) {
            showGuestState()
            return
        }

        showLoading(true)

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
                    showLoading(false)

                    if (challenges.isEmpty()) {
                        showEmptyState()
                    } else {
                        showChallenges(challenges)
                        updateHeader(challenges)
                        updateProgressCard(challenges)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading daily challenges: ${e.message}", e)
                runOnUiThread {
                    showLoading(false)
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

        showLoading(true)

        lifecycleScope.launch {
            try {
                val newChallenges = gamificationManager.generateDailyChallenges(userId)

                runOnUiThread {
                    showLoading(false)
                    challengesAdapter.updateChallenges(newChallenges)
                    updateHeader(newChallenges)
                    updateProgressCard(newChallenges)
                    Toast.makeText(this@DailyChallengesActivity, "Challenges refreshed! ✨", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing challenges: ${e.message}", e)
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@DailyChallengesActivity, "Failed to refresh challenges", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onChallengeClicked(challenge: DailyChallenge) {
        if (challenge.isCompleted) {
            Toast.makeText(this, "Challenge completed! 🎉 You earned ${challenge.pointsReward} points", Toast.LENGTH_SHORT).show()
        } else {
            val timeLeft = (challenge.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60)
            val progressText = when (challenge.type.name) {
                "TRANSACTION" -> "Add transactions to make progress"
                "RECEIPT" -> "Upload receipts with your transactions"
                "BUDGET_COMPLIANCE" -> "Keep your spending under budget"
                else -> "Complete this challenge to earn points"
            }

            Toast.makeText(this, "$progressText\n⏰ ${timeLeft}h remaining", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateHeader(challenges: List<DailyChallenge>) {
        val completedCount = challenges.count { it.isCompleted }
        val totalPoints = challenges.filter { it.isCompleted }.sumOf { it.pointsReward }

        // Update header stats
        binding.headerCompletedCount.text = completedCount.toString()
        binding.headerPointsEarned.text = totalPoints.toString()

        // Calculate and show streak (simplified for demo)
        binding.headerStreakCount.text = "3" // You can get this from user progress
    }

    private fun updateProgressCard(challenges: List<DailyChallenge>) {
        val completedCount = challenges.count { it.isCompleted }
        val totalCount = challenges.size
        val totalPoints = challenges.filter { it.isCompleted }.sumOf { it.pointsReward }

        binding.challengesStatusText.text = "$completedCount/$totalCount Complete"
        binding.pointsEarnedText.text = "$totalPoints pts"

        // Update progress bar
        val progress = if (totalCount > 0) (completedCount * 100) / totalCount else 0
        binding.challengesProgressBar.progress = progress

        // Update time remaining
        updateTimeRemaining()
    }

    private fun updateTimeRemaining() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val hoursLeft = 24 - currentHour
        binding.timeRemainingText.text = "${hoursLeft}h left"
    }

    private fun showLoading(show: Boolean) {
        binding.loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        binding.challengesRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
    }

    private fun showChallenges(challenges: List<DailyChallenge>) {
        binding.challengesRecyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        challengesAdapter.updateChallenges(challenges)
    }

    private fun showGuestState() {
        showLoading(false)
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "Log in to access daily challenges"
        binding.emptyStateSubtext.text = "Create an account to start earning points and completing challenges!"
        binding.emptyStateRefreshButton.text = "Sign In"
        binding.emptyStateRefreshButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun showEmptyState() {
        binding.challengesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "No challenges available"
        binding.emptyStateSubtext.text = "Check back tomorrow for new challenges!"
        binding.emptyStateRefreshButton.text = "Refresh Challenges"
    }

    private fun showErrorState() {
        binding.challengesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "Failed to load challenges"
        binding.emptyStateSubtext.text = "Please check your connection and try again"
        binding.emptyStateRefreshButton.text = "Try Again"
    }

    private fun isToday(timestamp: Long): Boolean {
        val today = Calendar.getInstance()
        val challengeDate = Calendar.getInstance().apply { timeInMillis = timestamp }

        return today.get(Calendar.YEAR) == challengeDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == challengeDate.get(Calendar.DAY_OF_YEAR)
    }
}