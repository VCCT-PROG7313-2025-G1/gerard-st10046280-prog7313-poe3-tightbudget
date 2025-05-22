package com.example.tightbudget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.Category
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.models.User

@Database(
    entities = [User::class, Category::class, Transaction::class, BudgetGoal::class, CategoryBudget::class],
    version = 4
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun budgetGoalDao(): BudgetGoalDao

    abstract fun categoryBudgetDao(): CategoryBudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tightbudget_db"
                )
                    // This is a placeholder for the migration strategy. (e.g., if you change the database schema)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}