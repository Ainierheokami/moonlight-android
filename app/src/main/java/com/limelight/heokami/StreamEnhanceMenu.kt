package com.limelight.heokami

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.View
import com.limelight.Game
import com.limelight.R
import com.limelight.nvstream.NvConnection

object StreamEnhanceMenu {
    private const val USE_VDD = "checkbox_stream_enhance_use_vdd"
    private const val SCREEN_MODE = "list_stream_enhance_screen_mode"
    private const val VDD_MODE = "list_stream_enhance_vdd_mode"
    private const val DISPLAY_NAME = "edittext_stream_enhance_display_name"
    private const val ROTATION_SYNC = "checkbox_stream_enhance_rotation_sync"
    private const val FORCE_RESUME = "checkbox_force_resume_current_session"
    private const val LAST_STREAM_DISPLAY_NAME = "last_stream_display_name"
    private const val LAST_STREAM_DISPLAY_USE_VDD = "last_stream_display_use_vdd"

    private val modeValues = listOf("-1", "0", "1")

    @JvmStatic
    fun show(game: Game, conn: NvConnection) {
        if (conn.isNvidiaServerSoftware) {
            Toast.makeText(game, R.string.game_menu_sunshine_required, Toast.LENGTH_LONG).show()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(game)
        val view = LayoutInflater.from(game).inflate(R.layout.dialog_stream_enhance, null)
        val modeGroup = view.findViewById<RadioGroup>(R.id.stream_enhance_mode_group)
        val screenMode = view.findViewById<Spinner>(R.id.stream_enhance_screen_mode)
        val vddMode = view.findViewById<Spinner>(R.id.stream_enhance_vdd_mode)
        val displayName = view.findViewById<EditText>(R.id.stream_enhance_display_name)
        val displaySpinner = view.findViewById<Spinner>(R.id.stream_enhance_display_spinner)
        val rotationSync = view.findViewById<CheckBox>(R.id.stream_enhance_rotation_sync)
        val forceResume = view.findViewById<CheckBox>(R.id.stream_enhance_force_resume)
        val modeHint = view.findViewById<TextView>(R.id.stream_enhance_mode_hint)
        val screenLabel = view.findViewById<TextView>(R.id.stream_enhance_screen_mode_label)
        val vddLabel = view.findViewById<TextView>(R.id.stream_enhance_vdd_mode_label)
        val displayLabel = view.findViewById<TextView>(R.id.stream_enhance_display_label)

        val labels = listOf(
            game.getString(R.string.stream_enhance_mode_auto),
            game.getString(R.string.stream_enhance_mode_primary),
            game.getString(R.string.stream_enhance_mode_secondary),
        )
        val adapter = ArrayAdapter(game, android.R.layout.simple_spinner_dropdown_item, labels)
        screenMode.adapter = adapter
        vddMode.adapter = adapter
        screenMode.setSelection(modeValues.indexOf(prefs.getString(SCREEN_MODE, "-1")).coerceAtLeast(0))
        vddMode.setSelection(modeValues.indexOf(prefs.getString(VDD_MODE, "-1")).coerceAtLeast(0))
        displayName.setText(prefs.getString(DISPLAY_NAME, ""))
        rotationSync.isChecked = prefs.getBoolean(ROTATION_SYNC, false)
        forceResume.isChecked = prefs.getBoolean(FORCE_RESUME, false)

        val currentUseVdd = prefs.getBoolean(USE_VDD, false)
        val currentDisplayName = prefs.getString(DISPLAY_NAME, "").orEmpty()
        modeGroup.check(
            when {
                currentUseVdd -> R.id.stream_enhance_mode_secondary_channel
                currentDisplayName.isNotBlank() -> R.id.stream_enhance_mode_custom
                else -> R.id.stream_enhance_mode_default
            }
        )

        fun setHostControlVisibility(modeId: Int) {
            val custom = modeId == R.id.stream_enhance_mode_custom
            modeHint.setText(
                when (modeId) {
                    R.id.stream_enhance_mode_secondary_channel -> R.string.stream_enhance_mode_secondary_channel_hint
                    R.id.stream_enhance_mode_custom -> R.string.stream_enhance_mode_custom_display_hint
                    else -> R.string.stream_enhance_mode_default_hint
                }
            )
            screenLabel.visibility = if (custom) View.VISIBLE else View.GONE
            screenMode.visibility = if (custom) View.VISIBLE else View.GONE
            vddLabel.visibility = if (custom) View.VISIBLE else View.GONE
            vddMode.visibility = if (custom) View.VISIBLE else View.GONE
            displayLabel.visibility = if (custom) View.VISIBLE else View.GONE
            displaySpinner.visibility = if (custom) View.VISIBLE else View.GONE
            displayName.visibility = if (custom) View.VISIBLE else View.GONE
        }
        modeGroup.setOnCheckedChangeListener { _, checkedId -> setHostControlVisibility(checkedId) }
        setHostControlVisibility(modeGroup.checkedRadioButtonId)

        val displayNames = mutableListOf(game.getString(R.string.stream_enhance_display_manual))
        val spinnerAdapter = ArrayAdapter(game, android.R.layout.simple_spinner_dropdown_item, displayNames)
        displaySpinner.adapter = spinnerAdapter
        Thread {
            val fetched = try {
                conn.displayNames
            } catch (_: Exception) {
                emptyList<String>()
            }
            Handler(Looper.getMainLooper()).post {
                displayNames.clear()
                displayNames.add(game.getString(R.string.stream_enhance_display_manual))
                displayNames.addAll(fetched)
                spinnerAdapter.notifyDataSetChanged()
                val index = displayNames.indexOf(currentDisplayName)
                if (index > 0) {
                    displaySpinner.setSelection(index)
                }
            }
        }.start()

        AlertDialog.Builder(game)
            .setTitle(R.string.game_menu_stream_enhance)
            .setView(view)
            .setNeutralButton(R.string.game_menu_sync_rotation_now) { _, _ ->
                prefs.edit().putBoolean(ROTATION_SYNC, true).apply()
                game.syncCurrentStreamRotation()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedDisplay = displayNames.getOrNull(displaySpinner.selectedItemPosition).orEmpty()
                val manualDisplay = displayName.text.toString().trim()
                val finalDisplayName = if (selectedDisplay != game.getString(R.string.stream_enhance_display_manual)) {
                    selectedDisplay
                } else {
                    manualDisplay
                }

                val editor = prefs.edit()
                    .putBoolean(ROTATION_SYNC, rotationSync.isChecked)
                    .putBoolean(FORCE_RESUME, forceResume.isChecked)
                    .remove(LAST_STREAM_DISPLAY_NAME)
                    .remove(LAST_STREAM_DISPLAY_USE_VDD)

                when (modeGroup.checkedRadioButtonId) {
                    R.id.stream_enhance_mode_secondary_channel -> editor
                        .putBoolean(USE_VDD, true)
                        .putString(SCREEN_MODE, "-1")
                        .putString(VDD_MODE, "1")
                        .putString(DISPLAY_NAME, "")
                    R.id.stream_enhance_mode_custom -> editor
                        .putBoolean(USE_VDD, false)
                        .putString(SCREEN_MODE, modeValues[screenMode.selectedItemPosition])
                        .putString(VDD_MODE, modeValues[vddMode.selectedItemPosition])
                        .putString(DISPLAY_NAME, finalDisplayName)
                    else -> editor
                        .putBoolean(USE_VDD, false)
                        .putString(SCREEN_MODE, "-1")
                        .putString(VDD_MODE, "-1")
                        .putString(DISPLAY_NAME, "")
                }

                editor.apply()
                Toast.makeText(game, R.string.game_menu_stream_enhance_saved, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
