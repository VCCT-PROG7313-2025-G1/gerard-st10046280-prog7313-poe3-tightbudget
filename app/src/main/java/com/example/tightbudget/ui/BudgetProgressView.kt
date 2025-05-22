package com.example.tightbudget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.tightbudget.R
import kotlin.math.min

/**
 * Custom view for visualizing budget progress with min/max indicators.
 * This view displays a progress bar representing the amount spent relative to the budget,
 * along with visual indicators for minimum and maximum budget thresholds.
 */
class BudgetProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Variables to store budget data
    private var spent: Float = 0f       // Amount spent
    private var maxBudget: Float = 1000f // Maximum budget
    private var minBudget: Float = 0f    // Minimum budget

    // Paint object for drawing the background bar
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.background_gray) // Background color
        style = Paint.Style.FILL // Fill the background
    }

    // Paint object for drawing the progress bar
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL // Fill the progress bar
    }

    // Paint object for drawing the dashed line for the minimum budget indicator
    private val minBudgetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.blue_light) // Light blue color
        style = Paint.Style.STROKE // Stroke style for dashed line
        strokeWidth = 2f // Line thickness
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f) // Dashed line effect
    }

    // Paint object for drawing the dashed line for the maximum budget indicator
    private val maxBudgetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.red_light) // Light red color
        style = Paint.Style.STROKE // Stroke style for dashed line
        strokeWidth = 2f // Line thickness
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f) // Dashed line effect
    }

    // Paint object for drawing the main text (e.g., spending amount)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_dark) // Dark text color
        textSize = 30f // Text size
        textAlign = Paint.Align.CENTER // Center alignment
    }

    // Paint object for drawing smaller text (e.g., "Min" and "Max" labels)
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_medium) // Medium text color
        textSize = 24f // Smaller text size
        textAlign = Paint.Align.CENTER // Center alignment
    }

    // Dimensions for drawing rounded corners and rectangles
    private val cornerRadius = 12f // Corner radius for rounded rectangles
    private val progressRect = RectF() // Rectangle for the progress bar
    private val backgroundRect = RectF() // Rectangle for the background bar

    /**
     * Updates the budget data and redraws the view.
     * @param spent The amount spent.
     * @param minBudget The minimum budget threshold.
     * @param maxBudget The maximum budget threshold.
     */
    fun setBudgetData(spent: Float, minBudget: Float, maxBudget: Float) {
        this.spent = spent
        this.minBudget = minBudget
        this.maxBudget = maxBudget.coerceAtLeast(minBudget + 1) // Ensure max > min

        // Set the progress bar color based on the spending status
        progressPaint.color = when {
            spent > maxBudget -> ContextCompat.getColor(context, R.color.red_light) // Over budget
            spent < minBudget -> ContextCompat.getColor(context, R.color.orange) // Below minimum
            else -> ContextCompat.getColor(context, R.color.teal_light) // Within budget
        }

        invalidate() // Request a redraw of the view
    }

    /**
     * Draws the custom view on the canvas.
     * @param canvas The canvas on which the view is drawn.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat() // Total width of the view
        val height = height.toFloat() // Total height of the view
        val progressHeight = height * 0.35f // Height of the progress bar (35% of total height)

        // Draw the background bar
        backgroundRect.set(0f, (height - progressHeight) / 2, width, (height + progressHeight) / 2)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

        // Calculate the width of the progress bar based on the spending
        val progressWidth = (spent / maxBudget.coerceAtLeast(1f)).coerceIn(0f, 1f) * width

        // Draw the progress bar if there is any progress
        if (progressWidth > 0) {
            progressRect.set(
                0f,
                (height - progressHeight) / 2,
                progressWidth,
                (height + progressHeight) / 2
            )
            canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)
        }

        // Draw the minimum budget indicator if applicable
        if (minBudget > 0) {
            val minX = (minBudget / maxBudget) * width // X-coordinate for the minimum budget
            canvas.drawLine(
                minX,
                (height - progressHeight) / 2 - 10f,
                minX,
                (height + progressHeight) / 2 + 10f,
                minBudgetPaint
            )

            // Draw the "Min" label above the indicator
            canvas.drawText(
                "Min",
                minX,
                (height - progressHeight) / 2 - 20f,
                smallTextPaint
            )
        }

        // Draw the maximum budget indicator
        canvas.drawLine(
            width,
            (height - progressHeight) / 2 - 10f,
            width,
            (height + progressHeight) / 2 + 10f,
            maxBudgetPaint
        )

        // Draw the "Max" label above the indicator
        canvas.drawText(
            "Max",
            width,
            (height - progressHeight) / 2 - 20f,
            smallTextPaint
        )

        // Format the spending amount as a string
        val spendingText = "R${String.format("%,.0f", spent)}"

        // Determine the X-coordinate for the spending text
        val textX = if (progressWidth > width * 0.3f) {
            min(progressWidth - 10f, width / 2) // Position within the progress bar
        } else {
            progressWidth + 10f // Position after the progress bar
        }

        // Adjust text color based on its position relative to the progress bar
        if (progressWidth > width * 0.3f && textX < progressWidth - 20f) {
            textPaint.color = Color.WHITE // Use white text if inside the progress bar
        } else {
            textPaint.color =
                ContextCompat.getColor(context, R.color.text_dark) // Use dark text otherwise
        }

        // Draw the spending amount text
        canvas.drawText(
            spendingText,
            textX,
            height / 2 + textPaint.textSize / 3,
            textPaint
        )
    }
}