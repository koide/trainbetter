package com.ditrain.app.ui.session

import android.content.Context
import androidx.appcompat.app.AlertDialog

class AbortConfirmDialogController(
    private val context: Context,
    private val onSaveInProgress: () -> Unit,
    private val onDiscard: () -> Unit,
) {
    fun show() {
        AlertDialog.Builder(context)
            .setTitle("Stop session?")
            .setMessage(
                "Save in progress: keep what you've logged so far; Home will offer Resume.\n\n" +
                "Discard: delete this session log entirely. Logged sets are lost.\n\n" +
                "Cancel: keep going."
            )
            .setPositiveButton("Save in progress") { _, _ -> onSaveInProgress() }
            .setNeutralButton("Cancel", null)
            .setNegativeButton("Discard") { _, _ -> onDiscard() }
            .show()
    }
}
