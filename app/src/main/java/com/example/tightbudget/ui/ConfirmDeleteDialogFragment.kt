package com.example.tightbudget.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tightbudget.R

/**
 * A DialogFragment that displays a confirmation dialog for delete actions.
 * It provides "Cancel" and "Confirm" buttons to either dismiss the dialog or proceed with the delete action.
 *
 * @param onConfirm Callback invoked when the "Confirm" button is clicked.
 */
class ConfirmDeleteDialogFragment(
    private val onConfirm: () -> Unit // Callback for confirming the delete action
) : DialogFragment() {

    /**
     * Creates and returns the dialog to be displayed.
     * This method inflates the custom layout and sets up the buttons with their respective actions.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Create an AlertDialog builder with a custom theme
        val builder = AlertDialog.Builder(requireContext(), R.style.TransparentDialogTheme)

        // Inflate the custom layout for the dialog
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_delete, null)

        // Find the "Cancel" button in the layout and set its click listener
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            dismiss() // Close the dialog when "Cancel" is clicked
        }

        // Find the "Confirm" button in the layout and set its click listener
        val confirmButton = view.findViewById<Button>(R.id.confirmDeleteButton)
        confirmButton.setOnClickListener {
            onConfirm() // Invoke the callback to handle the delete action
            dismiss() // Close the dialog after confirming
        }

        // Set the custom view for the dialog
        builder.setView(view)

        // Create and return the dialog
        return builder.create()
    }
}