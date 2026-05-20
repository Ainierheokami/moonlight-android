package com.limelight.heokami

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.limelight.Game
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.nvstream.NvConnection

/**
 * Runtime bitrate adjustment UI inspired by qiin2333/moonlight-vplus
 * BitrateCardController.kt. The slider mapping is reimplemented here and the
 * host command is routed through this project's NvConnection/NvHTTP stack.
 */
object StreamBitrateMenu {
    @JvmStatic
    fun show(game: Game, conn: NvConnection) {
        val view = LayoutInflater.from(game).inflate(R.layout.dialog_seekbar, null)
        val valueText = view.findViewById<TextView>(R.id.bitrate_value)
        val seekBar = view.findViewById<SeekBar>(R.id.bitrate_seekbar)
        val prefs = PreferenceManager.getDefaultSharedPreferences(game)
        seekBar.max = 59

        fun updateValue(progress: Int) {
            valueText.text = game.getString(R.string.game_menu_current_bitrate_mbps, progressToKbps(progress) / 1000f)
        }

        fun applyBitrate(progress: Int) {
            val bitrateKbps = progressToKbps(progress)
            LimeLog.info("Runtime bitrate UI apply requested: $bitrateKbps Kbps")
            conn.setBitrate(bitrateKbps) { success, error ->
                Handler(Looper.getMainLooper()).post {
                    if (success) {
                        prefs.edit().putInt("seekbar_bitrate_kbps", bitrateKbps).apply()
                        Toast.makeText(game, R.string.game_menu_bitrate_applied, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(game, error ?: game.getString(R.string.game_menu_bitrate_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        seekBar.progress = kbpsToProgress(conn.currentBitrate)
        updateValue(seekBar.progress)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                applyBitrate(seekBar.progress)
            }
        })

        AlertDialog.Builder(game)
            .setTitle(R.string.game_menu_adjust_bitrate)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.game_menu_bitrate_apply_now) { _, _ -> applyBitrate(seekBar.progress) }
            .show()
    }

    private fun progressToKbps(progress: Int): Int = when {
        progress <= 9 -> 500 + progress * 500
        progress <= 24 -> 5_000 + (progress - 9) * 1_000
        progress <= 39 -> 20_000 + (progress - 24) * 2_000
        progress <= 49 -> 50_000 + (progress - 39) * 5_000
        else -> 100_000 + (progress - 49) * 10_000
    }

    private fun kbpsToProgress(kbps: Int): Int = when {
        kbps <= 5_000 -> ((kbps - 500) / 500).coerceIn(0, 9)
        kbps <= 20_000 -> (9 + (kbps - 5_000) / 1_000).coerceIn(10, 24)
        kbps <= 50_000 -> (24 + (kbps - 20_000) / 2_000).coerceIn(25, 39)
        kbps <= 100_000 -> (39 + (kbps - 50_000) / 5_000).coerceIn(40, 49)
        else -> (49 + (kbps - 100_000) / 10_000).coerceIn(50, 59)
    }
}
