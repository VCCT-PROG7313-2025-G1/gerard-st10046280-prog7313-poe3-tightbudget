package com.example.tightbudget.adapters

    import android.animation.ObjectAnimator
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.ImageView
    import android.widget.ProgressBar
    import android.widget.TextView
    import androidx.core.content.ContextCompat
    import androidx.recyclerview.widget.RecyclerView
    import com.example.tightbudget.R
    import com.example.tightbudget.models.DailyChallenge
    import com.example.tightbudget.models.ChallengeType

    /**
     * Adapter for displaying daily challenges in a RecyclerView
     */
    class DailyChallengesAdapter(
        private var challenges: List<DailyChallenge>,
        private val onChallengeClick: (DailyChallenge) -> Unit
    ) : RecyclerView.Adapter<DailyChallengesAdapter.ChallengeViewHolder>() {

        class ChallengeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val challengeIcon: TextView = view.findViewById(R.id.challengeIcon)
            val challengeTitle: TextView = view.findViewById(R.id.challengeTitle)
            val challengeDescription: TextView = view.findViewById(R.id.challengeDescription)
            val challengePoints: TextView = view.findViewById(R.id.challengePoints)
            val challengeProgress: ProgressBar = view.findViewById(R.id.challengeProgress)
            val challengeProgressText: TextView = view.findViewById(R.id.challengeProgressText)
            val challengeStatus: ImageView = view.findViewById(R.id.challengeStatus)
            val challengeTimeLeft: TextView = view.findViewById(R.id.challengeTimeLeft)
            val completionOverlay: View = view.findViewById(R.id.completionOverlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_daily_challenge, parent, false)
            return ChallengeViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
            val challenge = challenges[position]
            val context = holder.itemView.context

            // Set challenge icon based on type
            holder.challengeIcon.text = getChallengeEmoji(challenge.type)

            // Set challenge details
            holder.challengeTitle.text = challenge.title
            holder.challengeDescription.text = challenge.description
            holder.challengePoints.text = "+${challenge.pointsReward} pts"

            // Calculate progress percentage (simplified approach)
            // Since your DailyChallenge model doesn't have currentProgress field,
            // we'll use a simplified approach or get it from the parent activity
            val currentProgress = if (challenge.isCompleted) {
                challenge.targetValue
            } else {
                // For demo purposes, calculate progress based on challenge type
                getCurrentProgressForChallenge(challenge)
            }

            val progressPercentage = if (challenge.targetValue > 0) {
                (currentProgress * 100) / challenge.targetValue
            } else 0

            // Animate progress bar
            val progress = progressPercentage.coerceAtMost(100)
            ObjectAnimator.ofInt(holder.challengeProgress, "progress", 0, progress).apply {
                duration = 1000
                start()
            }

            // Update progress text
            holder.challengeProgressText.text = "$currentProgress/${challenge.targetValue} completed"

            // Set completion status and styling
            if (challenge.isCompleted) {
                // Completed state
                holder.challengeStatus.setImageResource(R.drawable.ic_check_circle)
                holder.challengeStatus.setColorFilter(
                    ContextCompat.getColor(context, R.color.green_light)
                )
                holder.challengeTimeLeft.text = "Completed! 🎉"
                holder.challengeTimeLeft.setTextColor(
                    ContextCompat.getColor(context, android.R.color.white)
                )
                holder.challengeTimeLeft.setBackgroundResource(R.drawable.completed_badge_background)

                // Show completion overlay
                holder.completionOverlay.visibility = View.VISIBLE

                // Style as completed
                holder.challengeIcon.alpha = 0.8f
                holder.challengeTitle.setTextColor(
                    ContextCompat.getColor(context, R.color.green_light)
                )

                // Enhanced points styling for completed challenges
                holder.challengePoints.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                holder.challengePoints.backgroundTintList =
                    ContextCompat.getColorStateList(context, R.color.green_light)

            } else {
                // In progress state
                holder.completionOverlay.visibility = View.GONE
                holder.challengeIcon.alpha = 1.0f
                holder.challengeTitle.setTextColor(
                    ContextCompat.getColor(context, R.color.text_dark)
                )

                // Calculate time left
                val timeLeft = challenge.expiresAt - System.currentTimeMillis()
                val hoursLeft = (timeLeft / (1000 * 60 * 60)).toInt()

                if (timeLeft > 0) {
                    holder.challengeStatus.setImageResource(R.drawable.ic_clock)
                    holder.challengeStatus.setColorFilter(
                        ContextCompat.getColor(context, R.color.text_medium)
                    )

                    // Format time remaining with better colors
                    val timeText = when {
                        hoursLeft > 24 -> "${hoursLeft / 24}d ${hoursLeft % 24}h left"
                        hoursLeft > 0 -> "${hoursLeft}h left"
                        else -> {
                            val minutesLeft = (timeLeft / (1000 * 60)).toInt()
                            "${minutesLeft}m left"
                        }
                    }

                    holder.challengeTimeLeft.text = timeText
                    // Use white text on dark background for better readability
                    holder.challengeTimeLeft.setTextColor(
                        ContextCompat.getColor(context, android.R.color.white)
                    )
                    holder.challengeTimeLeft.setBackgroundResource(R.drawable.time_badge_background)

                    // Points styling for active challenges
                    holder.challengePoints.setTextColor(
                        ContextCompat.getColor(context, android.R.color.white)
                    )
                    holder.challengePoints.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.teal_light)

                } else {
                    // Expired state
                    holder.challengeStatus.setImageResource(R.drawable.ic_close)
                    holder.challengeStatus.setColorFilter(
                        ContextCompat.getColor(context, R.color.red_light)
                    )
                    holder.challengeTimeLeft.text = "Expired"
                    holder.challengeTimeLeft.setTextColor(
                        ContextCompat.getColor(context, android.R.color.white)
                    )
                    holder.challengeTimeLeft.setBackgroundResource(R.drawable.expired_badge_background)

                    // Dim expired challenges
                    holder.itemView.alpha = 0.6f

                    // Gray out points for expired challenges
                    holder.challengePoints.setTextColor(
                        ContextCompat.getColor(context, R.color.text_light)
                    )
                    holder.challengePoints.backgroundTintList =
                        ContextCompat.getColorStateList(context, R.color.gray_medium)
                }
            }

            // Set challenge type specific styling
            setChallengeTypeColors(holder, challenge.type, context)

            // Set click listener with animation
            holder.itemView.setOnClickListener {
                // Add ripple animation
                holder.itemView.isPressed = true
                holder.itemView.postDelayed({
                    holder.itemView.isPressed = false
                    onChallengeClick(challenge)
                }, 150)
            }
        }

        override fun getItemCount(): Int = challenges.size

        /**
         * Update the challenges list and refresh the adapter with animation
         */
        fun updateChallenges(newChallenges: List<DailyChallenge>) {
            challenges = newChallenges
            notifyDataSetChanged()
        }

        /**
         * Get emoji for challenge type
         */
        private fun getChallengeEmoji(type: ChallengeType): String {
            return when (type) {
                ChallengeType.TRANSACTION -> "📝"
                ChallengeType.RECEIPT -> "📄"
                ChallengeType.BUDGET_COMPLIANCE -> "💰"
                ChallengeType.SAVINGS -> "🏦"
                ChallengeType.STREAK -> "🔥"
                ChallengeType.CATEGORY_LIMIT -> "🛍️"
            }
        }

        /**
         * Get simplified current progress for a challenge
         * This is a basic implementation - in a real app you'd get this from the GamificationManager
         */
        private fun getCurrentProgressForChallenge(challenge: DailyChallenge): Int {
            // Simplified progress calculation for demo purposes
            // In your real implementation, you'd call GamificationManager methods here
            return when (challenge.type) {
                ChallengeType.TRANSACTION -> {
                    // Simulate some progress for transaction challenges
                    kotlin.random.Random.nextInt(0, challenge.targetValue)
                }
                ChallengeType.RECEIPT -> {
                    // Simulate some progress for receipt challenges
                    kotlin.random.Random.nextInt(0, challenge.targetValue)
                }
                ChallengeType.BUDGET_COMPLIANCE -> {
                    // For budget compliance, it's either 0 or target (binary)
                    if (kotlin.random.Random.nextBoolean()) challenge.targetValue else 0
                }
                else -> {
                    kotlin.random.Random.nextInt(0, challenge.targetValue)
                }
            }
        }

        /**
         * Set challenge type specific colors and styling
         */
        private fun setChallengeTypeColors(holder: ChallengeViewHolder, type: ChallengeType, context: android.content.Context) {
            val colorRes = when (type) {
                ChallengeType.TRANSACTION -> R.color.blue_light
                ChallengeType.RECEIPT -> R.color.green_light
                ChallengeType.BUDGET_COMPLIANCE -> R.color.purple_light
                ChallengeType.SAVINGS -> R.color.orange_light
                ChallengeType.STREAK -> R.color.red_light
                ChallengeType.CATEGORY_LIMIT -> R.color.teal_light
            }

            // Apply the color to the icon background
            holder.challengeIcon.backgroundTintList =
                ContextCompat.getColorStateList(context, colorRes)

            // Update progress bar color
            holder.challengeProgress.progressTintList =
                ContextCompat.getColorStateList(context, colorRes)
        }
    }