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
import com.limelight.nvstream.http.NvHTTP

object StreamEnhanceMenu {
    private const val USE_VDD = "checkbox_stream_enhance_use_vdd"
    private const val SCREEN_MODE = "list_stream_enhance_screen_mode"
    private const val VDD_MODE = "list_stream_enhance_vdd_mode"
    private const val DISPLAY_NAME = "edittext_stream_enhance_display_name"
    private const val ROTATION_SYNC = "checkbox_stream_enhance_rotation_sync"
    private const val FORCE_RESUME = "checkbox_force_resume_current_session"
    private const val LAST_STREAM_DISPLAY_NAME = "last_stream_display_name"
    private const val LAST_STREAM_DISPLAY_USE_VDD = "last_stream_display_use_vdd"
    private const val PHYSICAL_ONLY = "checkbox_stream_enhance_physical_only"
    private const val CACHED_PHYSICAL_GUID = "cached_physical_display_guid"

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
        val currentPhysicalOnly = prefs.getBoolean(PHYSICAL_ONLY, false)
        modeGroup.check(
            when {
                currentPhysicalOnly -> R.id.stream_enhance_mode_physical_only
                currentUseVdd -> R.id.stream_enhance_mode_secondary_channel
                currentDisplayName.isNotBlank() -> R.id.stream_enhance_mode_custom
                else -> R.id.stream_enhance_mode_default
            }
        )

        fun setHostControlVisibility(modeId: Int) {
            val custom = modeId == R.id.stream_enhance_mode_custom
            modeHint.setText(
                when (modeId) {
                    R.id.stream_enhance_mode_physical_only -> R.string.stream_enhance_mode_physical_only_hint
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

        val displays = mutableListOf<NvHTTP.DisplayInfo>()
        val displayNames = mutableListOf(game.getString(R.string.stream_enhance_display_manual))
        val spinnerAdapter = ArrayAdapter(game, android.R.layout.simple_spinner_dropdown_item, displayNames)
        displaySpinner.adapter = spinnerAdapter
        Thread {
            val fetched = try {
                conn.displays
            } catch (_: Exception) {
                emptyList<NvHTTP.DisplayInfo>()
            }
            Handler(Looper.getMainLooper()).post {
                displays.clear()
                displays.addAll(fetched)
                
                val detectedPhysical = fetched.firstOrNull {
                    val lowerName = it.displayName.lowercase()
                    val lowerFriendly = it.friendlyName.lowercase()
                    !lowerName.contains("zako") && !lowerName.contains("virtual") &&
                    !lowerFriendly.contains("zako") && !lowerFriendly.contains("virtual")
                }
                detectedPhysical?.let {
                    if (it.deviceId.isNotBlank()) {
                        prefs.edit().putString(CACHED_PHYSICAL_GUID, it.deviceId).apply()
                    }
                }

                displayNames.clear()
                displayNames.add(game.getString(R.string.stream_enhance_display_manual))
                displayNames.addAll(fetched.map { it.toString() })
                spinnerAdapter.notifyDataSetChanged()
                
                var selectIndex = 0
                for (i in fetched.indices) {
                    val info = fetched[i]
                    if ((info.deviceId.isNotBlank() && info.deviceId == currentDisplayName) || info.displayName == currentDisplayName) {
                        selectIndex = i + 1
                        break
                    }
                }
                if (selectIndex > 0) {
                    displaySpinner.setSelection(selectIndex)
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
                val selectedIndex = displaySpinner.selectedItemPosition
                val selectedInfo = if (selectedIndex > 0) displays.getOrNull(selectedIndex - 1) else null
                val manualDisplay = displayName.text.toString().trim()
                
                val finalDisplayName = if (selectedIndex > 0 && selectedInfo != null) {
                    if (selectedInfo.deviceId.isNotBlank()) selectedInfo.deviceId else selectedInfo.displayName
                } else {
                    manualDisplay
                }

                val editor = prefs.edit()
                    .putBoolean(ROTATION_SYNC, rotationSync.isChecked)
                    .putBoolean(FORCE_RESUME, forceResume.isChecked)
                    .remove(LAST_STREAM_DISPLAY_NAME)
                    .remove(LAST_STREAM_DISPLAY_USE_VDD)
                    .putBoolean(PHYSICAL_ONLY, false)

                val cachedGuid = prefs.getString(CACHED_PHYSICAL_GUID, "")

                when (modeGroup.checkedRadioButtonId) {
                    R.id.stream_enhance_mode_physical_only -> {
                        editor.putBoolean(PHYSICAL_ONLY, true)
                        editor.putBoolean(USE_VDD, false)
                        editor.putString(SCREEN_MODE, "-1")
                        editor.putString(VDD_MODE, "-1")
                        
                        val physicalDisplayInfo = displays.firstOrNull {
                            val lowerName = it.displayName.lowercase()
                            val lowerFriendly = it.friendlyName.lowercase()
                            !lowerName.contains("zako") && !lowerName.contains("virtual") &&
                            !lowerFriendly.contains("zako") && !lowerFriendly.contains("virtual")
                        }
                        val physicalDisplay = physicalDisplayInfo?.let {
                            if (it.deviceId.isNotBlank()) it.deviceId else it.displayName
                        } ?: cachedGuid?.takeIf { it.isNotBlank() } ?: "\\\\.\\DISPLAY1"
                        
                        editor.putString(DISPLAY_NAME, physicalDisplay)
                    }
                    R.id.stream_enhance_mode_secondary_channel -> editor
                        .putBoolean(USE_VDD, true)
                        .putString(SCREEN_MODE, "-1")
                        .putString(VDD_MODE, "1")
                        .putString(DISPLAY_NAME, "")
                    R.id.stream_enhance_mode_custom -> {
                        val finalDisplayToSave = if (finalDisplayName.trim().isEmpty()) {
                            val physicalDisplayInfo = displays.firstOrNull {
                                val lowerName = it.displayName.lowercase()
                                val lowerFriendly = it.friendlyName.lowercase()
                                !lowerName.contains("zako") && !lowerName.contains("virtual") &&
                                !lowerFriendly.contains("zako") && !lowerFriendly.contains("virtual")
                            }
                            val physicalDisplay = physicalDisplayInfo?.let {
                                if (it.deviceId.isNotBlank()) it.deviceId else it.displayName
                            } ?: cachedGuid?.takeIf { it.isNotBlank() } ?: "\\\\.\\DISPLAY1"
                            physicalDisplay
                        } else {
                            finalDisplayName
                        }
                        editor
                            .putBoolean(USE_VDD, false)
                            .putString(SCREEN_MODE, modeValues[screenMode.selectedItemPosition])
                            .putString(VDD_MODE, modeValues[vddMode.selectedItemPosition])
                            .putString(DISPLAY_NAME, finalDisplayToSave)
                    }
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
