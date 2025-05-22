package com.example.tightbudget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.tightbudget.models.Category

@Dao
interface CategoryDao {
    @Insert
    suspend fun insertCategory(category: Category)

    @Query("SELECT * FROM categories")
    suspend fun getAllCategories(): List<Category>

    @Insert
    suspend fun insertAll(categories: List<Category>)
}