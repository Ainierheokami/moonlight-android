package com.limelight.heokami;

import com.limelight.R;

enum GameMenuSection {
    STREAM(R.string.game_menu_section_stream),
    INPUT(R.string.game_menu_section_input_controls),
    HOTKEYS(R.string.game_menu_section_hotkeys),
    OVERLAY(R.string.game_menu_section_screen_overlay),
    PORTALS(R.string.game_menu_section_portals),
    CUSTOM(R.string.game_menu_section_custom_hotkeys);

    final int titleRes;
    GameMenuSection(int titleRes) {
        this.titleRes = titleRes;
    }
}
