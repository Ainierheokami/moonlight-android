package com.limelight.heokami

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.limelight.Game
import com.limelight.R
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.http.NvHTTP

/**
 * 串流增强菜单
 * 管理默认与自定义串流屏幕及主副屏拓扑切换
 */
object StreamEnhanceMenu {
    private const val USE_VDD = "checkbox_stream_enhance_use_vdd"
    private const val SCREEN_MODE = "list_stream_enhance_screen_mode"
    private const val VDD_MODE = "list_stream_enhance_vdd_mode"
    private const val DISPLAY_NAME = "edittext_stream_enhance_display_name"
    private const val ROTATION_SYNC = "checkbox_stream_enhance_rotation_sync"
    private const val FORCE_RESUME = "checkbox_force_resume_current_session"
    private const val CACHED_PHYSICAL_GUID = "cached_physical_display_guid"

    @JvmStatic
    fun show(game: Game, conn: NvConnection) {
        if (conn.isNvidiaServerSoftware) {
            Toast.makeText(game, R.string.game_menu_sunshine_required, Toast.LENGTH_LONG).show()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(game)
        val view = LayoutInflater.from(game).inflate(R.layout.dialog_stream_enhance, null)
        val modeGroup = view.findViewById<RadioGroup>(R.id.stream_enhance_mode_group)
        val rotationSync = view.findViewById<CheckBox>(R.id.stream_enhance_rotation_sync)
        val forceResume = view.findViewById<CheckBox>(R.id.stream_enhance_force_resume)
        val modeHint = view.findViewById<TextView>(R.id.stream_enhance_mode_hint)
        val customStatus = view.findViewById<TextView>(R.id.stream_enhance_custom_status)
        val container = view.findViewById<LinearLayout>(R.id.stream_enhance_displays_container)

        rotationSync.isChecked = prefs.getBoolean(ROTATION_SYNC, false)
        forceResume.isChecked = prefs.getBoolean(FORCE_RESUME, false)

        // 内存临时变量保存当前配置
        var tempDisplayName = prefs.getString(DISPLAY_NAME, "") ?: ""
        var tempUseVdd = prefs.getBoolean(USE_VDD, false)
        var tempScreenMode = prefs.getString(SCREEN_MODE, "-1") ?: "-1"
        var tempVddMode = prefs.getString(VDD_MODE, "-1") ?: "-1"

        // 智能判定大分类模式
        val isCustomModeInitial = tempDisplayName.isNotEmpty() || tempUseVdd
        modeGroup.check(
            if (isCustomModeInitial) R.id.stream_enhance_mode_custom else R.id.stream_enhance_mode_default
        )

        val displays = mutableListOf<NvHTTP.DisplayInfo>()
        val cachedGuid = prefs.getString(CACHED_PHYSICAL_GUID, "") ?: ""

        fun dp(value: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                game.resources.displayMetrics
            ).toInt()
        }

        fun refreshUiState() {
            if (modeGroup.checkedRadioButtonId == R.id.stream_enhance_mode_default) {
                customStatus.visibility = View.GONE
                container.visibility = View.GONE
                modeHint.setText(R.string.stream_enhance_mode_default_hint)
                return
            }

            customStatus.visibility = View.VISIBLE
            container.visibility = View.VISIBLE
            modeHint.setText(R.string.stream_enhance_mode_custom_display_hint)

            val primaryText: String
            val streamText: String

            if (tempDisplayName.isEmpty() && tempUseVdd) {
                primaryText = game.getString(R.string.stream_enhance_virtual_activate_title)
                streamText = game.getString(R.string.stream_enhance_virtual_activate_title)
            } else {
                val matched = displays.firstOrNull {
                    (it.deviceId != null && it.deviceId.isNotEmpty() && it.deviceId == tempDisplayName) || 
                    it.displayName == tempDisplayName
                }
                val displayNameToShow = matched?.toString() ?: if (tempDisplayName.isNotEmpty()) tempDisplayName else game.getString(R.string.stream_enhance_unknown)
                
                if (tempUseVdd) {
                    primaryText = if (tempVddMode == "0") displayNameToShow else "物理主屏"
                } else {
                    primaryText = if (tempScreenMode == "0") displayNameToShow else "物理主屏"
                }
                streamText = displayNameToShow
            }

            customStatus.text = game.getString(R.string.stream_enhance_status_format, primaryText, streamText)

            val childCount = container.childCount
            for (i in 0 until childCount) {
                val row = container.getChildAt(i) as? LinearLayout ?: continue
                val displayInfo = row.tag as? NvHTTP.DisplayInfo ?: continue
                val btnPrimary = row.findViewById<Button>(1001) ?: continue
                val btnStream = row.findViewById<Button>(1002) ?: continue

                val isThisVirtual = displayInfo.displayName.lowercase().contains("zako") || 
                                   displayInfo.displayName.lowercase().contains("virtual") || 
                                   displayInfo.friendlyName.lowercase().contains("zako") || 
                                   displayInfo.friendlyName.lowercase().contains("virtual") ||
                                   displayInfo.displayName == "virtual_fallback"

                val isStreamSelected = if (displayInfo.displayName == "virtual_fallback") {
                    tempDisplayName.isEmpty() && tempUseVdd
                } else {
                    (displayInfo.deviceId != null && displayInfo.deviceId.isNotEmpty() && displayInfo.deviceId == tempDisplayName) || 
                    displayInfo.displayName == tempDisplayName
                }

                val isPrimarySelected = isStreamSelected && (
                    if (isThisVirtual) tempVddMode == "0" else tempScreenMode == "0"
                )

                btnPrimary.setBackgroundResource(
                    if (isPrimarySelected) R.drawable.menu_header_background else R.drawable.button_background_dark
                )
                btnStream.setBackgroundResource(
                    if (isStreamSelected && !isPrimarySelected) R.drawable.menu_header_background else R.drawable.button_background_dark
                )
            }
        }

        fun rebuildDisplayList() {
            container.removeAllViews()

            for (info in displays) {
                val isThisVirtual = info.displayName.lowercase().contains("zako") || 
                                   info.displayName.lowercase().contains("virtual") || 
                                   info.friendlyName.lowercase().contains("zako") || 
                                   info.friendlyName.lowercase().contains("virtual") ||
                                   info.displayName == "virtual_fallback"

                val row = LinearLayout(game).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(6)
                    }
                    gravity = Gravity.CENTER_VERTICAL
                    tag = info
                }

                val tvName = TextView(game).apply {
                    text = if (info.displayName == "virtual_fallback") {
                        game.getString(R.string.stream_enhance_virtual_activate_title)
                    } else {
                        info.toString()
                    }
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                }
                row.addView(tvName)

                val btnPrimary = Button(game).apply {
                    id = 1001
                    text = game.getString(R.string.stream_enhance_set_primary)
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 11f
                    setPadding(dp(6), 0, dp(6), 0)
                    layoutParams = LinearLayout.LayoutParams(dp(76), dp(36)).apply {
                        rightMargin = dp(6)
                    }
                    setOnClickListener {
                        if (info.displayName == "virtual_fallback") {
                            tempDisplayName = ""
                            tempUseVdd = true
                            tempVddMode = "0"
                            tempScreenMode = "-1"
                        } else {
                            tempDisplayName = if (info.deviceId != null && info.deviceId.isNotEmpty()) info.deviceId else info.displayName
                            tempUseVdd = isThisVirtual
                            if (isThisVirtual) {
                                tempVddMode = "0"
                                tempScreenMode = "-1"
                            } else {
                                tempScreenMode = "0"
                                tempVddMode = "-1"
                            }
                        }
                        refreshUiState()
                    }
                }
                row.addView(btnPrimary)

                val btnStream = Button(game).apply {
                    id = 1002
                    text = game.getString(R.string.stream_enhance_stream_only)
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 11f
                    setPadding(dp(6), 0, dp(6), 0)
                    layoutParams = LinearLayout.LayoutParams(dp(76), dp(36))
                    setOnClickListener {
                        if (info.displayName == "virtual_fallback") {
                            tempDisplayName = ""
                            tempUseVdd = true
                            tempVddMode = "1"
                            tempScreenMode = "-1"
                        } else {
                            tempDisplayName = if (info.deviceId != null && info.deviceId.isNotEmpty()) info.deviceId else info.displayName
                            tempUseVdd = isThisVirtual
                            if (isThisVirtual) {
                                tempVddMode = "1"
                                tempScreenMode = "-1"
                            } else {
                                tempScreenMode = "1"
                                tempVddMode = "-1"
                            }
                        }
                        refreshUiState()
                    }
                }
                row.addView(btnStream)

                container.addView(row)
            }

            refreshUiState()
        }

        modeGroup.setOnCheckedChangeListener { _, _ -> refreshUiState() }

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
                    if (it.deviceId != null && it.deviceId.isNotEmpty()) {
                        prefs.edit().putString(CACHED_PHYSICAL_GUID, it.deviceId).apply()
                    }
                }

                val hasVirtual = fetched.any {
                    val lowerName = it.displayName.lowercase()
                    val lowerFriendly = it.friendlyName.lowercase()
                    lowerName.contains("zako") || lowerName.contains("virtual") ||
                    lowerFriendly.contains("zako") || lowerFriendly.contains("virtual")
                }
                if (!hasVirtual) {
                    displays.add(NvHTTP.DisplayInfo("virtual_fallback", "虚拟显示器 (强制激活)", ""))
                }

                val hasPhysical = fetched.any {
                    val lowerName = it.displayName.lowercase()
                    val lowerFriendly = it.friendlyName.lowercase()
                    !lowerName.contains("zako") && !lowerName.contains("virtual") &&
                    !lowerFriendly.contains("zako") && !lowerFriendly.contains("virtual")
                }
                if (!hasPhysical && cachedGuid.isNotEmpty()) {
                    displays.add(0, NvHTTP.DisplayInfo("\\\\.\\DISPLAY1", "物理主显示器", cachedGuid))
                }

                rebuildDisplayList()
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
                val editor = prefs.edit()
                    .putBoolean(ROTATION_SYNC, rotationSync.isChecked)
                    .putBoolean(FORCE_RESUME, forceResume.isChecked)

                if (modeGroup.checkedRadioButtonId == R.id.stream_enhance_mode_default) {
                    editor
                        .putBoolean(USE_VDD, false)
                        .putString(DISPLAY_NAME, "")
                        .putString(SCREEN_MODE, "-1")
                        .putString(VDD_MODE, "-1")
                } else {
                    editor
                        .putBoolean(USE_VDD, tempUseVdd)
                        .putString(DISPLAY_NAME, tempDisplayName)
                        .putString(SCREEN_MODE, tempScreenMode)
                        .putString(VDD_MODE, tempVddMode)
                }

                editor.apply()
                Toast.makeText(game, R.string.game_menu_stream_enhance_saved, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
