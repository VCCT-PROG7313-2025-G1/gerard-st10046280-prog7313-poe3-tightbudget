package com.example.tightbudget

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.adapters.BadgeAdapter
import com.example.tightbudget.models.Badge

/**
 * AchievementsActivity is responsible for displaying the user's achievements in a grid format.
 * It includes a RecyclerView for badges, filter buttons for filtering badges, and a back button to exit the activity.
 */
class AchievementsActivity : AppCompatActivity() {

    // RecyclerView to display the list of badges
    private lateinit var badgeRecyclerView: RecyclerView

    // Adapter to bind badge data to the RecyclerView
    private lateinit var badgeAdapter: BadgeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        // Set up the back button to close the activity when clicked
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish() // Ends the activity and returns to the previous screen
        }

        // Load the list of badges (currently static data)
        val badgeList = loadBadges()

        // Initialize the RecyclerView and set its adapter and layout manager
        badgeRecyclerView = findViewById(R.id.badgeRecyclerView)
        badgeAdapter = BadgeAdapter(this, badgeList)
        badgeRecyclerView.adapter = badgeAdapter
        badgeRecyclerView.layoutManager = GridLayoutManager(this, 3) // Grid layout with 3 columns

        // Set up the filter buttons to filter badges based on their category
        setupFilters(badgeList)
    }

    /**
     * Loads a static list of badges for demonstration purposes.
     * This will eventually be replaced with dynamic data from a database or API.
     * @return A list of Badge objects representing the user's achievements.
     */
    private fun loadBadges(): List<Badge> {
        return listOf(
            Badge("Saver", "Level 10", true), // Earned badge
            Badge("Consistent", "4 weeks", true), // Earned badge
            Badge("Transport", "5 days", true), // Earned badge
            Badge("Locked", "Keep tracking", false), // Locked badge
            Badge("Locked", "Keep tracking", false), // Locked badge
            Badge("Locked", "Keep tracking", false) // Locked badge
        )
    }

    /**
     * Sets up the filter buttons to allow users to filter badges by category.
     * Each button updates the RecyclerView with a filtered list of badges.
     */
    private fun setupFilters(allBadges: List<Badge>) {
        // Show all badges when the "All" filter button is clicked
        findViewById<Button>(R.id.allFilterButton).setOnClickListener {
            badgeAdapter.updateList(allBadges) // Update the adapter with the full list
        }

        // Filter badges containing "Budget" in their name
        findViewById<Button>(R.id.budgetFilterButton).setOnClickListener {
            badgeAdapter.updateList(allBadges.filter { it.name.contains("Budget", ignoreCase = true) })
        }

        // Filter badges containing "Save" in their name
        findViewById<Button>(R.id.savingsFilterButton).setOnClickListener {
            badgeAdapter.updateList(allBadges.filter { it.name.contains("Save", ignoreCase = true) })
        }

        // Filter badges containing "Consistent" in their name
        findViewById<Button>(R.id.consistencyFilterButton).setOnClickListener {
            badgeAdapter.updateList(allBadges.filter { it.name.contains("Consistent", ignoreCase = true) })
        }
    }
}