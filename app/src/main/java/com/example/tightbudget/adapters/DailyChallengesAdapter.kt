package com.example.tightbudget.adapters

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
            val challengeStatus: ImageView = view.findViewById(R.id.challengeStatus)
            val challengeTimeLeft: TextView = view.findViewById(R.id.challengeTimeLeft)
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

            // Set completion status
            if (challenge.isCompleted) {
                holder.challengeStatus.setImageResource(R.drawable.ic_check_circle)
                holder.challengeStatus.setColorFilter(
                    ContextCompat.getColor(context, R.color.green_light)
                )
                holder.challengeProgress.progress = 100
                holder.challengeTimeLeft.text = "Completed! 🎉"
                holder.challengeTimeLeft.setTextColor(
                    ContextCompat.getColor(context, R.color.green_light)
                )

                // Dim completed challenges
                holder.itemView.alpha = 0.7f
            } else {
                holder.challengeStatus.setImageResource(R.drawable.ic_clock)
                holder.challengeStatus.setColorFilter(
                    ContextCompat.getColor(context, R.color.orange_light)
                )

                // Calculate and show time left
                val timeLeft = challenge.expiresAt - System.currentTimeMillis()
                val hoursLeft = timeLeft / (1000 * 60 * 60)
                val minutesLeft = (timeLeft % (1000 * 60 * 60)) / (1000 * 60)

                when {
                    timeLeft <= 0 -> {
                        holder.challengeTimeLeft.text = "Expired"
                        holder.challengeTimeLeft.setTextColor(
                            ContextCompat.getColor(context, R.color.red_light)
                        )
                        holder.itemView.alpha = 0.5f
                    }
                    hoursLeft > 0 -> {
                        holder.challengeTimeLeft.text = "${hoursLeft}h ${minutesLeft}m left"
                        holder.challengeTimeLeft.setTextColor(
                            ContextCompat.getColor(context, R.color.text_medium)
                        )
                    }
                    else -> {
                        holder.challengeTimeLeft.text = "${minutesLeft}m left"
                        holder.challengeTimeLeft.setTextColor(
                            ContextCompat.getColor(context, R.color.orange_light)
                        )
                    }
                }

                // TODO: Calculate actual progress based on current user activity
                // For now, show random progress
                val progress = kotlin.random.Random.nextInt(0, challenge.targetValue + 1)
                val progressPercentage = (progress * 100) / challenge.targetValue
                holder.challengeProgress.progress = progressPercentage

                holder.itemView.alpha = 1.0f
            }

            // Set click listener
            holder.itemView.setOnClickListener {
                onChallengeClick(challenge)
            }

            // Add visual styling based on challenge type
            when (challenge.type) {
                ChallengeType.TRANSACTION -> {
                    holder.itemView.setBackgroundResource(R.drawable.challenge_background_blue)
                }
                ChallengeType.RECEIPT -> {
                    holder.itemView.setBackgroundResource(R.drawable.challenge_background_green)
                }
                ChallengeType.BUDGET_COMPLIANCE -> {
                    holder.itemView.setBackgroundResource(R.drawable.challenge_background_purple)
                }
                ChallengeType.SAVINGS -> {
                    holder.itemView.setBackgroundResource(R.drawable.challenge_background_gold)
                }
                else -> {
                    holder.itemView.setBackgroundResource(R.drawable.challenge_background_default)
                }
            }
        }

        override fun getItemCount(): Int = challenges.size

        fun updateChallenges(newChallenges: List<DailyChallenge>) {
            challenges = newChallenges
            notifyDataSetChanged()
        }

        private fun getChallengeEmoji(challengeType: ChallengeType): String {
            return when (challengeType) {
                ChallengeType.TRANSACTION -> "📝"
                ChallengeType.RECEIPT -> "📄"
                ChallengeType.BUDGET_COMPLIANCE -> "🎯"
                ChallengeType.SAVINGS -> "💰"
                ChallengeType.STREAK -> "🔥"
                ChallengeType.CATEGORY_LIMIT -> "📊"
            }
        }
    }