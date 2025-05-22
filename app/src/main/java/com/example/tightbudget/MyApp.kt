package com.example.tightbudget

import android.app.Application
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.models.Category
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        seedDefaultCategories()
    }

    private fun seedDefaultCategories() {
        val db = AppDatabase.getDatabase(this)
        val categoryDao = db.categoryDao()

        CoroutineScope(Dispatchers.IO).launch {
            val existingCategories = categoryDao.getAllCategories()
            if (existingCategories.isEmpty()) {
                val defaultCategories = listOf(
                    Category(
                        name = "Food",
                        emoji = EmojiUtils.getCategoryEmoji("Food"),
                        color = "#FF9800",
                        budget = 2500.0
                    ),
                    Category(
                        name = "Housing",
                        emoji = EmojiUtils.getCategoryEmoji("Housing"),
                        color = "#4CAF50",
                        budget = 6000.0
                    ),
                    Category(
                        name = "Transport",
                        emoji = EmojiUtils.getCategoryEmoji("Transport"),
                        color = "#2196F3",
                        budget = 1500.0
                    ),
                    Category(
                        name = "Entertainment",
                        emoji = EmojiUtils.getCategoryEmoji("Entertainment"),
                        color = "#9C27B0",
                        budget = 800.0
                    ),
                    Category(
                        name = "Utilities",
                        emoji = EmojiUtils.getCategoryEmoji("Utilities"),
                        color = "#FFC107",
                        budget = 1200.0
                    ),
                    Category(
                        name = "Health",
                        emoji = EmojiUtils.getCategoryEmoji("Health"),
                        color = "#E91E63",
                        budget = 1000.0
                    ),
                    Category(
                        name = "Shopping",
                        emoji = EmojiUtils.getCategoryEmoji("Shopping"),
                        color = "#00BCD4",
                        budget = 1500.0
                    ),
                    Category(
                        name = "Education",
                        emoji = EmojiUtils.getCategoryEmoji("Education"),
                        color = "#3F51B5",
                        budget = 2000.0
                    )
                )
                categoryDao.insertAll(defaultCategories)
            }
        }
    }
}