package com.limelight.heokami

import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.limelight.Game
import com.limelight.R

object StreamAudioVolumeMenu {
    private const val STEP_PERCENT = 5

    @JvmStatic
    fun show(game: Game) {
        val view = LayoutInflater.from(game).inflate(R.layout.dialog_seekbar, null)
        val valueText = view.findViewById<TextView>(R.id.bitrate_value)
        val seekBar = view.findViewById<SeekBar>(R.id.bitrate_seekbar)
        seekBar.max = (Game.STREAM_AUDIO_GAIN_MAX_PERCENT - Game.STREAM_AUDIO_GAIN_MIN_PERCENT) / STEP_PERCENT

        fun progressToGain(progress: Int): Int =
            Game.STREAM_AUDIO_GAIN_MIN_PERCENT + progress * STEP_PERCENT

        fun gainToProgress(gainPercent: Int): Int =
            ((gainPercent.coerceIn(Game.STREAM_AUDIO_GAIN_MIN_PERCENT, Game.STREAM_AUDIO_GAIN_MAX_PERCENT)
                    - Game.STREAM_AUDIO_GAIN_MIN_PERCENT) / STEP_PERCENT)

        fun updateValue(gainPercent: Int) {
            valueText.text = game.getString(R.string.game_menu_current_audio_volume_percent, gainPercent)
        }

        fun applyGain(gainPercent: Int) {
            game.setStreamAudioGainPercent(gainPercent)
            updateValue(game.streamAudioGainPercent)
            seekBar.progress = gainToProgress(game.streamAudioGainPercent)
            Toast.makeText(game, R.string.game_menu_audio_volume_applied, Toast.LENGTH_SHORT).show()
        }

        seekBar.progress = gainToProgress(game.streamAudioGainPercent)
        updateValue(game.streamAudioGainPercent)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateValue(progressToGain(progress))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                applyGain(progressToGain(seekBar.progress))
            }
        })

        AlertDialog.Builder(game)
            .setTitle(R.string.game_menu_audio_volume)
            .setMessage(R.string.game_menu_audio_volume_hint)
            .setView(view)
            .setNegativeButton(R.string.game_menu_audio_volume_100) { _, _ -> applyGain(100) }
            .setNeutralButton(R.string.game_menu_audio_volume_150) { _, _ -> applyGain(150) }
            .setPositiveButton(R.string.game_menu_audio_volume_200) { _, _ -> applyGain(200) }
            .show()
    }
}
