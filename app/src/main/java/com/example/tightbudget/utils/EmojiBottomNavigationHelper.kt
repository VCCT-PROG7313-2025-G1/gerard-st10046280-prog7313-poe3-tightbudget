//package com.example.tightbudget.utils
//
//import android.util.Log
//import android.view.MenuItem
//import com.google.android.material.bottomnavigation.BottomNavigationView
//import com.example.tightbudget.R
//
///**
// * Helper class for handling bottom navigation with emoji icons
// */
//object EmojiBottomNavigationHelper {
//
//    /**
//     * Set up the bottom navigation with emoji icons in menu titles
//     * This approach uses the title field to display emojis instead of accessing internal views
//     */
////    fun setupWithEmojis(
////        bottomNav: BottomNavigationView,
////        onNavigationItemSelected: (MenuItem) -> Boolean
////    ) {
////        Log.d("TightBudget", "Setting up BottomNavigation with emojis")
////
////        // Set emojis as titles for the menu items
////        setEmojiTitles(bottomNav)
////
////        // Set up listener that delegates to the provided callback
////        bottomNav.setOnItemSelectedListener { menuItem ->
////            // Skip handling the center item used for FAB
////            if (menuItem.itemId == R.id.nav_add) {
////                return@setOnItemSelectedListener false
////            }
////
////            // Call the provided callback
////            onNavigationItemSelected(menuItem)
////        }
////
////        // Initial selection
////        updateSelectedItem(bottomNav, bottomNav.selectedItemId)
////    }
//
//    /**
//     * Set emoji characters as the title for each menu item
//     */
//    private fun setEmojiTitles(bottomNav: BottomNavigationView) {
//        for (i in 0 until bottomNav.menu.size()) {
//            val menuItem = bottomNav.menu.getItem(i)
//
//            // Skip the center item used for FAB
//            if (menuItem.itemId == R.id.nav_add) {
//                menuItem.title = ""
//                menuItem.isEnabled = false
//                continue
//            }
//
//            // Set the emoji as the title
//            val emoji = getEmojiForItem(menuItem.itemId)
//            menuItem.title = emoji
//
//            // Log the emoji being set
//            Log.d("TightBudget", "Setting emoji for menu item ${menuItem.itemId}: $emoji")
//        }
//    }
//
//    /**
//     * Update visual state when an item is selected
//     */
//    private fun updateSelectedItem(bottomNav: BottomNavigationView, selectedId: Int) {
//        // The BottomNavigationView will handle the selection state automatically
//        // We just need to ensure our titles are set correctly
//        for (i in 0 until bottomNav.menu.size()) {
//            val menuItem = bottomNav.menu.getItem(i)
//
//            // Skip the center item
//            if (menuItem.itemId == R.id.nav_add) {
//                continue
//            }
//
//            // Get the appropriate emoji
//            val emoji = getEmojiForItem(menuItem.itemId)
//            menuItem.title = emoji
//        }
//
//        Log.d("TightBudget", "Updated selected item: $selectedId")
//    }
//
//    /**
//     * Get the appropriate emoji for a menu item
//     */
//    private fun getEmojiForItem(itemId: Int): String {
//        return when (itemId) {
//            R.id.nav_dashboard -> EmojiUtils.getNavigationEmoji("home")
//            R.id.nav_reports -> EmojiUtils.getNavigationEmoji("chart")
//            R.id.nav_wallet -> EmojiUtils.getNavigationEmoji("wallet")
//            R.id.nav_settings -> EmojiUtils.getNavigationEmoji("settings")
//            else -> "📝" // Default emoji
//        }
//    }
//}