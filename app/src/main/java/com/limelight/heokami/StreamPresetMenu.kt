package com.limelight.heokami

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import com.limelight.Game
import com.limelight.R
import com.limelight.nvstream.NvConnection
import org.json.JSONObject

/**
 * Local multi-scene preset implementation. It follows the feature idea from
 * qiin2333/moonlight-vplus while storing this project's existing preference
 * keys directly, so switching presets stays compatible with StreamSettings.
 */
object StreamPresetMenu {
    private const val PRESET_NAMES = "stream_scene_preset_names"
    private const val PRESET_PREFIX = "stream_scene_preset_"

    private val keys = listOf(
        "list_resolution",
        "list_fps",
        "seekbar_bitrate_kbps",
        "checkbox_enable_sops",
        "checkbox_host_audio",
        "checkbox_enable_hdr",
        "video_format",
        "checkbox_stream_enhance_use_vdd",
        "list_stream_enhance_screen_mode",
        "list_stream_enhance_vdd_mode",
        "edittext_stream_enhance_display_name",
        "checkbox_stream_enhance_rotation_sync",
        "checkbox_force_resume_current_session",
    )

    @JvmStatic
    fun show(game: Game, conn: NvConnection) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(game)
        val names = prefs.getStringSet(PRESET_NAMES, emptySet()).orEmpty().sorted()
        val labels = mutableListOf(game.getString(R.string.game_menu_preset_save_current))
        labels.addAll(names)

        AlertDialog.Builder(game)
            .setTitle(R.string.game_menu_stream_presets)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    promptSave(game)
                } else {
                    showPresetActions(game, conn, labels[which])
                }
            }
            .show()
    }

    private fun promptSave(game: Game) {
        val input = EditText(game)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setSingleLine(true)
        input.hint = game.getString(R.string.game_menu_preset_name_hint)

        AlertDialog.Builder(game)
            .setTitle(R.string.game_menu_preset_save_current)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    savePreset(game, name)
                }
            }
            .show()
    }

    private fun savePreset(context: Context, name: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = JSONObject()
        for (key in keys) {
            when (val value = prefs.all[key]) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is String -> json.put(key, value)
            }
        }

        val names = prefs.getStringSet(PRESET_NAMES, emptySet()).orEmpty().toMutableSet()
        names.add(name)
        prefs.edit()
            .putStringSet(PRESET_NAMES, names)
            .putString(PRESET_PREFIX + name, json.toString())
            .apply()
        Toast.makeText(context, R.string.game_menu_preset_saved, Toast.LENGTH_SHORT).show()
    }

    private fun showPresetActions(game: Game, conn: NvConnection, name: String) {
        val actions = arrayOf(
            game.getString(R.string.game_menu_preset_apply),
            game.getString(R.string.game_menu_preset_overwrite),
            game.getString(R.string.game_menu_preset_rename),
            game.getString(R.string.game_menu_preset_delete),
        )
        AlertDialog.Builder(game)
            .setTitle(name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> applyPreset(game, conn, name)
                    1 -> savePreset(game, name)
                    2 -> promptRename(game, name)
                    3 -> deletePreset(game, name)
                }
            }
            .show()
    }

    private fun promptRename(game: Game, oldName: String) {
        val input = EditText(game)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setSingleLine(true)
        input.setText(oldName)

        AlertDialog.Builder(game)
            .setTitle(R.string.game_menu_preset_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    renamePreset(game, oldName, newName)
                }
            }
            .show()
    }

    private fun renamePreset(context: Context, oldName: String, newName: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldValue = prefs.getString(PRESET_PREFIX + oldName, null) ?: return
        val names = prefs.getStringSet(PRESET_NAMES, emptySet()).orEmpty().toMutableSet()
        names.remove(oldName)
        names.add(newName)
        prefs.edit()
            .putStringSet(PRESET_NAMES, names)
            .remove(PRESET_PREFIX + oldName)
            .putString(PRESET_PREFIX + newName, oldValue)
            .apply()
        Toast.makeText(context, R.string.game_menu_preset_saved, Toast.LENGTH_SHORT).show()
    }

    private fun deletePreset(context: Context, name: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val names = prefs.getStringSet(PRESET_NAMES, emptySet()).orEmpty().toMutableSet()
        names.remove(name)
        prefs.edit()
            .putStringSet(PRESET_NAMES, names)
            .remove(PRESET_PREFIX + name)
            .apply()
        Toast.makeText(context, R.string.game_menu_preset_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun applyPreset(game: Game, conn: NvConnection, name: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(game)
        val json = JSONObject(prefs.getString(PRESET_PREFIX + name, "{}") ?: "{}")
        val editor = prefs.edit()
        for (key in keys) {
            if (!json.has(key)) continue
            when (val value = json.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()

        if (json.has("seekbar_bitrate_kbps")) {
            val bitrate = json.optInt("seekbar_bitrate_kbps", conn.currentBitrate)
            conn.setBitrate(bitrate) { _, _ -> }
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(game, R.string.game_menu_preset_applied_reconnect, Toast.LENGTH_LONG).show()
        }
    }
}
