package com.limelight.heokami;

import android.view.View;

final class GameMenuAction {
    final String id;
    final int titleRes;
    final int iconRes;
    final GameMenuSection section;
    final int priority;
    final boolean danger;
    final boolean visible;
    final boolean enabled;
    final View.OnClickListener onClick;
    final String overrideTitle;

    GameMenuAction(String id, int titleRes, int iconRes, GameMenuSection section, int priority,
               boolean danger, boolean visible, boolean enabled, View.OnClickListener onClick) {
        this(id, titleRes, iconRes, section, priority, danger, visible, enabled, onClick, null);
    }

    GameMenuAction(String id, int titleRes, int iconRes, GameMenuSection section, int priority,
               boolean danger, boolean visible, boolean enabled, View.OnClickListener onClick,
               String overrideTitle) {
        this.id = id;
        this.titleRes = titleRes;
        this.iconRes = iconRes;
        this.section = section;
        this.priority = priority;
        this.danger = danger;
        this.visible = visible;
        this.enabled = enabled;
        this.onClick = onClick;
        this.overrideTitle = overrideTitle;
    }
}
