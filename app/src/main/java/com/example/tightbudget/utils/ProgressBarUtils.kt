package com.example.tightbudget.utils

import android.content.Context
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.example.tightbudget.R
import com.example.tightbudget.models.Category

/**
 * Utility class for creating and customizing progress bars
 */
object ProgressBarUtils {

    /**
     * Sets progress based on current vs goal value, applies styling too.
     */
    fun setProgress(progressBar: ProgressBar, current: Double, goal: Double) {
        val percentage = if (goal != 0.0) ((current / goal) * 100).toInt() else 0
        progressBar.progress = percentage.coerceAtMost(100)

        // Optional styling based on progress range
        applyBudgetStatusProgressBar(progressBar, progressBar.context, current.toFloat(), goal.toFloat())
    }

    /**
     * Apply a category-colored progress bar style.
     */
    fun applyCategoryProgressBar(progressBar: ProgressBar, context: Context, categoryName: String) {
        val color = DrawableUtils.getCategoryColor(context, categoryName)
        applyColoredProgressBar(progressBar, context, color)
    }

    /**
     * Apply a status-colored progress bar style based on budget usage percentage.
     */
    fun applyBudgetStatusProgressBar(progressBar: ProgressBar, context: Context, spent: Float, budget: Float) {
        val color = DrawableUtils.getBudgetStatusColor(context, spent, budget)
        applyColoredProgressBar(progressBar, context, color)
    }

    /**
     * Creates and applies a custom progress bar drawable with the specified colour.
     */
    fun applyColoredProgressBar(progressBar: ProgressBar, context: Context, progressColor: Int) {
        val cornerRadius = context.resources.displayMetrics.density * 3 // 3dp

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(context, R.color.background_gray))
            this.cornerRadius = cornerRadius
        }

        val progressDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(progressColor)
            this.cornerRadius = cornerRadius
        }

        val clipDrawable = ClipDrawable(progressDrawable, Gravity.START, ClipDrawable.HORIZONTAL)

        val layers = arrayOf<Drawable>(backgroundDrawable, clipDrawable)
        val layerDrawable = LayerDrawable(layers).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

        progressBar.progressDrawable = layerDrawable
    }
}