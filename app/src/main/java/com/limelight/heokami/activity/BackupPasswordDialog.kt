package com.limelight.heokami.activity

import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.limelight.R

/** Password prompts for the pairing backup / restore flow. Callers must wipe the returned array. */
object BackupPasswordDialog {
    private fun CharSequence.toCharArrayCopy(): CharArray {
        val out = CharArray(length)
        for (i in indices) out[i] = this[i]
        return out
    }

    private fun passwordField(activity: AppCompatActivity, hintRes: Int): EditText =
        EditText(activity).apply {
            setHint(hintRes)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }

    private fun container(activity: AppCompatActivity, vararg views: android.view.View): LinearLayout {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            views.forEach {
                addView(it, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    /** Ask for a new backup password. An empty array means "no password: back up settings only". */
    fun askNewPassword(activity: AppCompatActivity, onResult: (CharArray) -> Unit, onCancel: () -> Unit) {
        val password = passwordField(activity, R.string.backup_password_hint)
        val confirm = passwordField(activity, R.string.backup_password_confirm_hint)
        val message = TextView(activity).apply { setText(R.string.backup_password_message) }
        val error = TextView(activity).apply {
            setText(R.string.backup_password_mismatch)
            setTextColor(0xFFFF6B6B.toInt())
            visibility = android.view.View.GONE
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.backup_password_title)
            .setView(container(activity, message, password, confirm, error))
            .setPositiveButton(R.string.backup_action_backup, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = password.text.toCharArrayCopy()
            val b = confirm.text.toCharArrayCopy()
            if (a.contentEquals(b)) {
                b.fill('\u0000')
                dialog.dismiss()
                onResult(a)
            } else {
                a.fill('\u0000'); b.fill('\u0000')
                error.visibility = android.view.View.VISIBLE
            }
        }
    }

    /** Ask for the password of an existing backup; [onSkip] restores settings only. */
    fun askExistingPassword(
        activity: AppCompatActivity,
        wrongPassword: Boolean,
        onPassword: (CharArray) -> Unit,
        onSkip: () -> Unit,
        onCancel: () -> Unit
    ) {
        val password = passwordField(activity, R.string.backup_password_hint)
        val message = TextView(activity).apply {
            setText(if (wrongPassword) R.string.restore_password_wrong else R.string.restore_password_message)
            if (wrongPassword) setTextColor(0xFFFF6B6B.toInt())
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.restore_password_title)
            .setView(container(activity, message, password))
            .setPositiveButton(R.string.restore_action_restore) { _, _ -> onPassword(password.text.toCharArrayCopy()) }
            .setNeutralButton(R.string.restore_skip_credentials) { _, _ -> onSkip() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }
}
