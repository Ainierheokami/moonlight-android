package com.limelight.heokami;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import android.app.Fragment;

import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.heokami.FloatingVirtualKeyboardFragment;
import com.limelight.portal.PortalConfig;
import com.limelight.portal.PortalManagerView;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 游戏菜单Fragment
 * 提供侧边滑出式菜单，包含各种游戏控制功能
 */
public class GameMenuFragment extends Fragment {

    private static final String PREF_LAST_STREAM_DISPLAY_LABEL = "last_stream_display_label";

    private Game game;
    private NvConnection conn;
    private View menuPanel;
    private View backgroundView;
    // 主列表与二级面板
    private View mainScrollView;
    private View touchModePanel;
    private TextView touchModeCurrentView;
    private LinearLayout statusContainer;
    private LinearLayout dashboardContainer;
    private LinearLayout touchModeOptions;
    private boolean isMenuVisible = false;
    private boolean showFromLeft = false;
    private long lastDisconnectTapMs = 0;
    private android.animation.ValueAnimator holdAnimator = null;

    // 动画持续时间
    private static final int ANIMATION_DURATION = 300;

    /**
     * 创建新的GameMenuFragment实例
     * @param game 游戏Activity实例
     * @param conn 网络连接实例
     * @return GameMenuFragment实例
     */
    public static GameMenuFragment newInstance(Game game, NvConnection conn) {
        return newInstance(game, conn, false);
    }

    public static GameMenuFragment newInstance(Game game, NvConnection conn, boolean showFromLeft) {
        GameMenuFragment fragment = new GameMenuFragment();
        fragment.game = game;
        fragment.conn = conn;
        fragment.showFromLeft = showFromLeft;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.game_menu_overlay, container, false);
        
        // 初始化视图
        initViews(view);
        // 设置菜单宽度
        setupMenuWidth();
        
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 设置点击事件
        setupClickListeners();
        
        // 显示菜单动画
        showMenuWithAnimation();
    }

    /**
     * 初始化视图组件
     */
    private void initViews(View view) {
        menuPanel = view.findViewById(R.id.menu_panel);
        backgroundView = view.findViewById(R.id.menu_background);
        mainScrollView = view.findViewById(R.id.main_scroll);
        touchModePanel = view.findViewById(R.id.touch_mode_sheet);
        touchModeCurrentView = view.findViewById(R.id.touch_mode_current);
        statusContainer = view.findViewById(R.id.status_container);
        dashboardContainer = view.findViewById(R.id.dashboard_container);
        touchModeOptions = view.findViewById(R.id.touch_mode_options);
        renderStatusBar();
        renderDashboard();
        renderTouchModeOptions();
    }

    /**
     * 设置菜单宽度和初始位置
     */
    private void setupMenuWidth() {
        // 获取屏幕尺寸
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // 获取当前屏幕方向
        int orientation = getResources().getConfiguration().orientation;
        
        int maxWidthPx = (int) (480 * getResources().getDisplayMetrics().density);
        int minWidthPx = (int) (320 * getResources().getDisplayMetrics().density);
        int targetWidth = (int) (screenWidth * (screenWidth > screenHeight ? 0.38f : 0.86f));
        int menuWidth = Math.min(Math.max(targetWidth, minWidthPx), Math.min(screenWidth, maxWidthPx));
        
        android.util.Log.d("GameMenu", "Screen width: " + screenWidth + ", Screen height: " + screenHeight + 
                          ", Orientation: " + orientation + ", Max width px: " + maxWidthPx + 
                          ", Menu width: " + menuWidth + ", Density: " + getResources().getDisplayMetrics().density);
        
        // 立即设置宽度，避免布局闪烁
        ViewGroup.LayoutParams params = menuPanel.getLayoutParams();
        params.width = menuWidth;
        menuPanel.setLayoutParams(params);
        
        RelativeLayout.LayoutParams relativeParams = menuPanel.getLayoutParams() instanceof RelativeLayout.LayoutParams
                ? (RelativeLayout.LayoutParams) menuPanel.getLayoutParams()
                : new RelativeLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        relativeParams.width = menuWidth;
        relativeParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
        relativeParams.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
        relativeParams.removeRule(RelativeLayout.ALIGN_PARENT_END);
        relativeParams.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        if (showFromLeft) {
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            relativeParams.setMarginStart(dp(12));
            relativeParams.setMarginEnd(0);
            relativeParams.leftMargin = dp(12);
            relativeParams.rightMargin = 0;
            menuPanel.setTranslationX(-menuWidth);
        } else {
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            relativeParams.setMarginStart(0);
            relativeParams.setMarginEnd(dp(12));
            relativeParams.leftMargin = 0;
            relativeParams.rightMargin = dp(12);
            menuPanel.setTranslationX(menuWidth);
        }
        menuPanel.setLayoutParams(relativeParams);
        
        // 确保菜单面板不可见，直到动画开始
        menuPanel.setVisibility(View.INVISIBLE);
    }

    /**
     * 设置点击事件监听器
     */
    private void setupClickListeners() {
        backgroundView.setOnClickListener(v -> hideMenuWithAnimation());

        View closeButton = getView().findViewById(R.id.btn_close_menu);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> hideMenuWithAnimation());
        }

        setupBottomButtons();
    }

    private enum MenuSection {
        STREAM(R.string.game_menu_section_stream),
        INPUT(R.string.game_menu_section_input_controls),
        HOTKEYS(R.string.game_menu_section_hotkeys),
        OVERLAY(R.string.game_menu_section_screen_overlay),
        PORTALS(R.string.game_menu_section_portals),
        CUSTOM(R.string.game_menu_section_custom_hotkeys);

        final int titleRes;
        MenuSection(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    private static final class MenuAction {
        final String id;
        final int titleRes;
        final int iconRes;
        final MenuSection section;
        final int priority;
        final boolean danger;
        final boolean visible;
        final boolean enabled;
        final View.OnClickListener onClick;
        final String overrideTitle;

        MenuAction(String id, int titleRes, int iconRes, MenuSection section, int priority,
                   boolean danger, boolean visible, boolean enabled, View.OnClickListener onClick) {
            this(id, titleRes, iconRes, section, priority, danger, visible, enabled, onClick, null);
        }

        MenuAction(String id, int titleRes, int iconRes, MenuSection section, int priority,
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

    private interface SliderApplyCallback {
        void apply(int value);
    }

    private static final class MenuSlider {
        final int titleRes;
        final int min;
        final int max;
        final int step;
        final int defaultValue;
        final int currentValue;
        final SliderApplyCallback applyCallback;

        MenuSlider(int titleRes, int min, int max, int step, int defaultValue,
                   int currentValue, SliderApplyCallback applyCallback) {
            this.titleRes = titleRes;
            this.min = min;
            this.max = max;
            this.step = step;
            this.defaultValue = defaultValue;
            this.currentValue = currentValue;
            this.applyCallback = applyCallback;
        }
    }

    private int dp(int value) {
        android.content.res.Resources resources = game != null ? game.getResources() : getResources();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.getDisplayMetrics());
    }

    private void renderStatusBar() {
        if (statusContainer == null) return;
        statusContainer.removeAllViews();
        
        // 1. 触控模式 (保留)
        addStatusChip(getString(R.string.game_menu_change_touch), getTouchModeName());
        
        // 2. 串流画质 (分辨率与帧率)
        String quality = "未知";
        if (game.getPrefConfig() != null) {
            quality = game.getPrefConfig().width + "x" + game.getPrefConfig().height + "  " + game.getPrefConfig().fps + "帧";
        }
        addStatusChip("串流画质", quality);
        
        // 3. 视频码率
        String bitrate = "未知";
        if (conn != null) {
            bitrate = String.format(java.util.Locale.getDefault(), "%.1f Mbps", conn.getCurrentBitrate() / 1000f);
        } else if (game.getPrefConfig() != null) {
            bitrate = String.format(java.util.Locale.getDefault(), "%.1f Mbps", game.getPrefConfig().bitrate / 1000f);
        }
        addStatusChip("视频码率", bitrate);

        // 4. 串流音量（客户端播放增益，按主机保存）
        addStatusChip(getString(R.string.game_menu_audio_volume_short), game.getStreamAudioGainLabel());
        
        // 5. 当前时间
        String currentTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
        addStatusChip("当前时间", currentTime);
    }

    private void addStatusChip(String label, String value) {
        TextView chip = new TextView(game);
        chip.setText(label + "\n" + value);
        chip.setTextColor(0xFFE6E6E6);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.menu_panel_background);
        chip.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        statusContainer.addView(chip, lp);
    }

    private void renderDashboard() {
        if (dashboardContainer == null) return;
        dashboardContainer.removeAllViews();

        List<MenuAction> actions = buildMenuActions();
        for (MenuSection section : MenuSection.values()) {
            List<MenuAction> sectionActions = new ArrayList<>();
            for (MenuAction action : actions) {
                if (action.visible && action.section == section) {
                    sectionActions.add(action);
                }
            }
            if (sectionActions.isEmpty()) continue;
            sectionActions.sort(Comparator.comparingInt(a -> a.priority));
            addSection(section, sectionActions);
        }
    }

    private List<MenuSlider> buildMenuSliders(MenuSection section) {
        List<MenuSlider> sliders = new ArrayList<>();
        if (section == MenuSection.STREAM) {
            sliders.add(new MenuSlider(
                    R.string.game_menu_audio_volume,
                    Game.STREAM_AUDIO_GAIN_MIN_PERCENT,
                    Game.STREAM_AUDIO_GAIN_MAX_PERCENT,
                    10,
                    Game.STREAM_AUDIO_GAIN_DEFAULT_PERCENT,
                    game.getStreamAudioGainPercent(),
                    value -> {
                        game.setStreamAudioGainPercent(value);
                        renderStatusBar();
                    }));
        }
        else if (section == MenuSection.INPUT) {
            sliders.add(new MenuSlider(
                    R.string.game_menu_touchpad_sensitivity,
                    10,
                    300,
                    5,
                    PreferenceConfiguration.DEFAULT_TOUCHPAD_SENSITIVITY,
                    game.getTouchpadSensitivityPercent(),
                    value -> {
                        game.setTouchpadSensitivityPercent(value);
                        renderStatusBar();
                    }));
        }
        return sliders;
    }

    private List<MenuAction> buildMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
        actions.add(new MenuAction("bitrate", R.string.game_menu_adjust_bitrate_short, 0, MenuSection.STREAM, 10, false, true, true, v -> {
            hideMenuWithAnimation();
            StreamBitrateMenu.show(game, conn);
        }));
        actions.add(new MenuAction("presets", R.string.game_menu_stream_presets_short, 0, MenuSection.STREAM, 30, false, true, true, v -> {
            hideMenuWithAnimation();
            StreamPresetMenu.show(game, conn);
        }));
        actions.add(new MenuAction("stream_enhance", R.string.game_menu_stream_enhance, 0, MenuSection.STREAM, 40, false, true, true, v -> {
            hideMenuWithAnimation();
            StreamEnhanceMenu.show(game, conn);
        }));
        actions.add(new MenuAction("switch_display", 0, 0, MenuSection.STREAM, 50, false, true, true, v -> {
            hideMenuWithAnimation();
            showSwitchDisplayDialog();
        }, "实时切换屏幕"));

        actions.add(new MenuAction("ime", R.string.game_menu_enable_keyboard, R.drawable.ic_keyboard, MenuSection.INPUT, 10, false, true, true, v -> {
            hideMenuWithAnimation();
            enableKeyboard();
        }));
        actions.add(new MenuAction("floating_keyboard", R.string.game_menu_floating_keyboard, R.drawable.ic_floating_keyboard, MenuSection.INPUT, 20, false, true, true, v -> {
            hideMenuWithAnimation();
            try {
                FloatingVirtualKeyboardFragment.Companion.show(game);
            } catch (Exception e) {
                Log.e("GameMenuFragment", "Error showing floating keyboard", e);
            }
        }));
        actions.add(new MenuAction("full_keyboard", R.string.game_menu_full_keyboard, R.drawable.ic_full_keyboard, MenuSection.INPUT, 30, false, true, true, v -> {
            hideMenuWithAnimation();
            VirtualKeyboardDialogFragment fragment = new VirtualKeyboardDialogFragment();
            fragment.show(game.getFragmentManager(), "VirtualKeyboard");
        }));
        actions.add(new MenuAction("send_clipboard", R.string.game_menu_send_clipboard_content, R.drawable.ic_clipboard, MenuSection.INPUT, 40, false, true, true, v -> {
            hideMenuWithAnimation();
            conn.sendUtf8Text(getClipboardContentAsString(game, new int[]{3}, new long[]{30}));
        }));

        actions.add(new MenuAction("copy", R.string.game_menu_copy, R.drawable.ic_copy, MenuSection.HOTKEYS, 10, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_C.getCode()})));
        actions.add(new MenuAction("paste", R.string.game_menu_paste, R.drawable.ic_paste, MenuSection.HOTKEYS, 20, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_V.getCode()})));
        actions.add(new MenuAction("screen_keyboard", R.string.game_menu_virtual_keyboard_short, R.drawable.ic_keyboard, MenuSection.HOTKEYS, 30, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_O.getCode()})));
        actions.add(new MenuAction("alt_tab", R.string.game_menu_switch_window_short, R.drawable.ic_switch_window, MenuSection.HOTKEYS, 40, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_TAB.getCode()})));
        actions.add(new MenuAction("home", R.string.game_menu_hotkey_home, R.drawable.ic_home, MenuSection.HOTKEYS, 50, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_D.getCode()})));

        actions.add(new MenuAction("controller", R.string.game_menu_toggle_virtual_controller, 0, MenuSection.OVERLAY, 10, false, true, true, v -> {
            hideMenuWithAnimation();
            game.toggleVirtualController();
        }));
        actions.add(new MenuAction("virtual_keyboard", R.string.game_menu_toggle_virtual_keyboard, 0, MenuSection.OVERLAY, 20, false, true, true, v -> {
            hideMenuWithAnimation();
            game.toggleVirtualKeyboard();
            Toast.makeText(game, game.getString(R.string.game_menu_toggle_virtual_keyboard_toast), Toast.LENGTH_SHORT).show();
        }));
        actions.add(new MenuAction("edit_virtual_keyboard", R.string.game_menu_edit_virtual_keyboard, 0, MenuSection.OVERLAY, 30, false, true, true, v -> openVirtualKeyboardEditor()));
        actions.add(new MenuAction("perf", R.string.game_menu_toggle_perf_overlay, 0, MenuSection.OVERLAY, 40, false, true, true, v -> {
            hideMenuWithAnimation();
            game.togglePerfOverlay();
        }));

        actions.add(new MenuAction("portal_toggle", game.arePortalsEnabled() ? R.string.game_menu_portal_disable : R.string.game_menu_portal_enable, 0, MenuSection.PORTALS, 10, false, true, game.getPortalManagerView() != null, v -> togglePortals()));
        actions.add(new MenuAction("portal_add", R.string.game_menu_portal_add, 0, MenuSection.PORTALS, 20, false, true, game.getPortalManagerView() != null, v -> addPortal()));
        actions.add(new MenuAction("portal_edit", R.string.game_menu_portal_toggle_edit, 0, MenuSection.PORTALS, 30, false, true, game.getPortalManagerView() != null, v -> togglePortalEditMode()));
        actions.add(new MenuAction("portal_manage", R.string.game_menu_portal_manage, 0, MenuSection.PORTALS, 40, false, true, game.getPortalManagerView() != null, v -> showPortalManagerDialog()));

        actions.add(new MenuAction("edit_hotkeys", R.string.game_menu_edit_hotkeys, 0, MenuSection.CUSTOM, 10, false, true, true, v -> openCustomHotkeyManager()));
        List<CustomHotkeysManager.CustomHotkey> customItems = CustomHotkeysManager.load(game);
        int priority = 20;
        for (CustomHotkeysManager.CustomHotkey item : customItems) {
            actions.add(new MenuAction("custom_" + item.name, 0, 0, MenuSection.CUSTOM, priority++, false, true, true, v -> runCustomHotkey(item), item.name));
        }
        return actions;
    }

    private void addSection(MenuSection section, List<MenuAction> actions) {
        TextView title = new TextView(game);
        title.setText(section.titleRes);
        title.setTextColor(0xFFB8B8B8);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(4));
        dashboardContainer.addView(title);

        for (MenuSlider slider : buildMenuSliders(section)) {
            dashboardContainer.addView(createSliderRow(slider));
        }

        GridLayout grid = new GridLayout(game);
        grid.setColumnCount(2);
        dashboardContainer.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (MenuAction action : actions) {
            grid.addView(createActionButton(action));
        }
    }

    private View createSliderRow(MenuSlider slider) {
        LinearLayout row = new LinearLayout(game);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(8), dp(6));
        row.setBackgroundResource(R.drawable.button_background_dark);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        rowParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.setLayoutParams(rowParams);

        TextView label = new TextView(game);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        label.setSingleLine(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(label, labelParams);

        SeekBar seekBar = new SeekBar(game);
        seekBar.setMax((slider.max - slider.min) / slider.step);
        seekBar.setProgress(valueToProgress(slider, slider.currentValue));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(seekBar, seekParams);

        Button resetButton = new Button(game);
        resetButton.setAllCaps(false);
        resetButton.setText(R.string.game_menu_slider_reset);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        resetButton.setTextColor(0xFFEAF2FF);
        resetButton.setPadding(0, 0, 0, 0);
        resetButton.setBackgroundResource(R.drawable.button_background_dark);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(42), dp(34));
        resetParams.setMargins(dp(6), 0, 0, 0);
        row.addView(resetButton, resetParams);

        final int[] currentValue = new int[]{progressToValue(slider, seekBar.getProgress())};
        updateSliderLabel(label, slider, currentValue[0]);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentValue[0] = progressToValue(slider, progress);
                updateSliderLabel(label, slider, currentValue[0]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                slider.applyCallback.apply(currentValue[0]);
            }
        });
        resetButton.setOnClickListener(v -> {
            seekBar.setProgress(valueToProgress(slider, slider.defaultValue));
            currentValue[0] = slider.defaultValue;
            updateSliderLabel(label, slider, currentValue[0]);
            slider.applyCallback.apply(slider.defaultValue);
        });

        return row;
    }

    private int valueToProgress(MenuSlider slider, int value) {
        int clampedValue = Math.max(slider.min, Math.min(slider.max, value));
        return (clampedValue - slider.min) / slider.step;
    }

    private int progressToValue(MenuSlider slider, int progress) {
        return Math.max(slider.min, Math.min(slider.max, slider.min + progress * slider.step));
    }

    private void updateSliderLabel(TextView label, MenuSlider slider, int value) {
        label.setText(getString(slider.titleRes) + "\n" + value + "%");
    }

    private Button createActionButton(MenuAction action) {
        Button button = new Button(game);
        button.setAllCaps(false);
        button.setText(action.overrideTitle != null ? action.overrideTitle : getString(action.titleRes));
        button.setTextColor(action.enabled ? 0xFFFFFFFF : 0xFF7D8797);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setEnabled(action.enabled);
        button.setMaxLines(2);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        button.setPadding(dp(10), 0, dp(8), 0);
        button.setBackgroundResource(action.danger ? R.drawable.button_background_red_dark : R.drawable.button_background_dark);
        if (action.iconRes != 0) {
            button.setCompoundDrawablesWithIntrinsicBounds(action.iconRes, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(8));
        }
        button.setOnClickListener(action.onClick);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(42);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(lp);
        return button;
    }

    private void runHotkey(short[] keys) {
        hideMenuWithAnimation();
        sendKeys(keys);
    }

    private void openVirtualKeyboardEditor() {
        hideMenuWithAnimation();
        VirtualKeyboard vk = game.getVirtualKeyboard();
        if (vk != null) {
            vk.show();
            vk.enterEditMode();
            new Handler(Looper.getMainLooper()).postDelayed(() -> new EditMenu(game, vk), ANIMATION_DURATION + 50);
        } else {
            Toast.makeText(game, "无法进入编辑模式：虚拟键盘未就绪", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCustomHotkeyManager() {
        hideMenuWithAnimation();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            VirtualKeyboard vk = game.getVirtualKeyboard();
            if (vk == null) {
                Toast.makeText(game, "无法编辑：虚拟键盘未就绪", Toast.LENGTH_SHORT).show();
                return;
            }
            CustomHotkeysManager.showManageDialog(game, vk, this::renderDashboard);
        }, ANIMATION_DURATION + 50);
    }

    private void runCustomHotkey(CustomHotkeysManager.CustomHotkey item) {
        hideMenuWithAnimation();
        VirtualKeyboard vk = game.getVirtualKeyboard();
        if (vk == null) {
            Toast.makeText(game, "无法执行：虚拟键盘未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        CustomHotkeysManager.runCustomHotkey(game, vk, item);
    }

    /**
     * 设置底部操作按钮
     */
    private void setupBottomButtons() {
        Button btnChangeTouch = getView().findViewById(R.id.btn_change_touch);
        if (btnChangeTouch != null) {
            btnChangeTouch.setOnClickListener(v -> toggleTouchModePanel());
        }

        Button btnDisconnect = getView().findViewById(R.id.btn_disconnect);
        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(null);
            btnDisconnect.setOnLongClickListener(null);
            
            btnDisconnect.setOnTouchListener(new View.OnTouchListener() {
                private long downTime = 0;
                private boolean isLongPressed = false;
                private boolean isCancelled = false;
                private boolean isTransitioned = false;
                private int originalTextColor = 0xFFFFFFFF;
                private final Handler longPressHandler = new Handler(Looper.getMainLooper());
                
                private final Runnable longPressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        isLongPressed = true;
                        
                        // 1. 触发物理震动反馈
                        try {
                            android.os.Vibrator vibrator = (android.os.Vibrator) game.getSystemService(Context.VIBRATOR_SERVICE);
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                                } else {
                                    vibrator.vibrate(80);
                                }
                            }
                        } catch (Exception ignored) {}
                        
                        // 2. 播放瞬间收编坍塌与淡出的强退过渡动画
                        btnDisconnect.animate()
                            .scaleX(0.7f)
                            .scaleY(0.7f)
                            .alpha(0.0f)
                            .setDuration(180)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    hideMenuWithAnimation();
                                    game.quitAndDisconnect();
                                }
                            })
                            .start();
                    }
                };

                private final Runnable transitionRunnable = new Runnable() {
                    @Override
                    public void run() {
                        isTransitioned = true;
                        originalTextColor = btnDisconnect.getCurrentTextColor();
                        
                        // 1. 改变文案为“退出串流”
                        String quitText = "zh".equals(java.util.Locale.getDefault().getLanguage()) ? "退出串流" : "Quit Stream";
                        btnDisconnect.setText(quitText);
                        btnDisconnect.setTextColor(originalTextColor);
                        btnDisconnect.setBackgroundResource(R.drawable.button_background_warning_dark);
                    }
                };

                private void restoreOriginalState() {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    longPressHandler.removeCallbacks(transitionRunnable);
                    
                    if (isTransitioned) {
                        isTransitioned = false;
                        btnDisconnect.setText(R.string.game_menu_disconnect);
                        btnDisconnect.setTextColor(originalTextColor);
                        btnDisconnect.setBackgroundResource(R.drawable.button_background_red_dark);
                    }
                }

                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    switch (event.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            downTime = android.os.SystemClock.elapsedRealtime();
                            isLongPressed = false;
                            isCancelled = false;
                            isTransitioned = false;
                            
                            // 延时 1000ms（1秒）执行长按退出任务
                            longPressHandler.postDelayed(longPressRunnable, 1000);
                            // 延时 200ms 执行文案和背景变色提示“退出串流”
                            longPressHandler.postDelayed(transitionRunnable, 200);
                            
                            // 启动 1000ms 慢收缩充能微动画 (1.0 -> 0.88, Alpha: 1.0 -> 0.6)
                            if (holdAnimator != null) {
                                holdAnimator.cancel();
                            }
                            holdAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 0.88f);
                            holdAnimator.setDuration(1000);
                            holdAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
                            holdAnimator.addUpdateListener(animation -> {
                                float val = (float) animation.getAnimatedValue();
                                btnDisconnect.setScaleX(val);
                                btnDisconnect.setScaleY(val);
                                float alpha = 1.0f - (1.0f - val) / (1.0f - 0.88f) * 0.4f;
                                btnDisconnect.setAlpha(alpha);
                            });
                            holdAnimator.start();
                            return true;
                            
                        case android.view.MotionEvent.ACTION_MOVE:
                            if (isCancelled || isLongPressed) return true;
                            
                            // 检测滑出边界防误触
                            float x = event.getX();
                            float y = event.getY();
                            if (x < 0 || x > v.getWidth() || y < 0 || y > v.getHeight()) {
                                isCancelled = true;
                                restoreOriginalState();
                                if (holdAnimator != null) {
                                    holdAnimator.cancel();
                                }
                                
                                // 平滑阻尼回弹
                                btnDisconnect.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .alpha(1.0f)
                                    .setDuration(250)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                                    .start();
                            }
                            return true;
                            
                        case android.view.MotionEvent.ACTION_UP:
                            restoreOriginalState();
                            if (holdAnimator != null) {
                                holdAnimator.cancel();
                            }
                            
                            if (isLongPressed) {
                                return true;
                            }
                            
                            // 平滑阻尼回弹
                            btnDisconnect.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .alpha(1.0f)
                                .setDuration(250)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                                .start();
                            
                            // 短按双击判断
                            long duration = android.os.SystemClock.elapsedRealtime() - downTime;
                            if (duration < 500 && !isCancelled) {
                                confirmDisconnect();
                            }
                            return true;
                            
                        case android.view.MotionEvent.ACTION_CANCEL:
                            restoreOriginalState();
                            if (holdAnimator != null) {
                                holdAnimator.cancel();
                            }
                            
                            // 平滑弹性回弹
                            btnDisconnect.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .alpha(1.0f)
                                .setDuration(250)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                                .start();
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    private void updateTouchModeSummary() {
        if (touchModeCurrentView == null || game == null) {
            return;
        }
        int mode = game.getCurrentTouchMode();
        String modeName;
        if (mode == 0) {
            modeName = getString(R.string.game_menu_touch_mode_multi_touch);
        } else if (mode == 1) {
            modeName = getString(R.string.game_menu_touch_mode_trackpad);
        } else {
            modeName = getString(R.string.game_menu_touch_mode_mouse);
        }
        touchModeCurrentView.setText(getString(R.string.game_menu_touch_mode_current, modeName));
    }

    private String getTouchModeName() {
        int mode = game.getCurrentTouchMode();
        if (mode == 0) return getString(R.string.game_menu_touch_mode_multi_touch);
        if (mode == 1) return getString(R.string.game_menu_touch_mode_trackpad);
        return getString(R.string.game_menu_touch_mode_mouse);
    }

    private void renderTouchModeOptions() {
        if (touchModeOptions == null) return;
        touchModeOptions.removeAllViews();
        addTouchModeButton(0, R.string.game_menu_touch_mode_multi_touch);
        addTouchModeButton(1, R.string.game_menu_touch_mode_trackpad);
        addTouchModeButton(2, R.string.game_menu_touch_mode_mouse);
        updateTouchModeSummary();
    }

    private void addTouchModeButton(int mode, int titleRes) {
        Button button = new Button(game);
        button.setAllCaps(false);
        button.setText(titleRes);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setMaxLines(1);
        button.setBackgroundResource(game.getCurrentTouchMode() == mode ? R.drawable.menu_header_background : R.drawable.button_background_dark);
        button.setOnClickListener(v -> {
            game.changeTouchMode(mode);
            renderStatusBar();
            renderTouchModeOptions();
            if (touchModePanel != null) touchModePanel.setVisibility(View.GONE);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        touchModeOptions.addView(button, lp);
    }

    private void toggleTouchModePanel() {
        if (touchModePanel == null) return;
        renderTouchModeOptions();
        touchModePanel.setVisibility(touchModePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void confirmDisconnect() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastDisconnectTapMs < 2200) {
            hideMenuWithAnimation();
            game.finish();
            return;
        }
        lastDisconnectTapMs = now;
        Toast.makeText(game, R.string.game_menu_disconnect_confirm, Toast.LENGTH_SHORT).show();
    }

    private void togglePortals() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager != null) {
            boolean enabled = portalManager.togglePortalsEnabled();
            game.postNotification(enabled ? getString(R.string.game_menu_portal_enable) : getString(R.string.game_menu_portal_disable), 2000);
        } else {
            Toast.makeText(game, "portalManager 为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void addPortal() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager == null) {
            Toast.makeText(game, "portalManager 为空", Toast.LENGTH_SHORT).show();
            return;
        }
        PortalConfig config = new PortalConfig();
        config.id = portalManager.generateNewId();
        config.srcRect = new RectF(0.2f, 0.2f, 0.4f, 0.4f);
        config.dstRect = createDefaultPortalTargetRect();
        config.enabled = true;
        config.name = "画面映射 " + config.id;
        if (!portalManager.arePortalsEnabled()) {
            portalManager.setPortalsEnabled(true);
        }
        portalManager.addPortal(config);
        portalManager.setPortalEditingMode(config.id, 1);
        game.postNotification("已添加画面映射，请调整源区域", 2000);
    }

    private RectF createDefaultPortalTargetRect() {
        View streamView = game.getStreamView();
        int width = streamView != null && streamView.getWidth() > 0 ? streamView.getWidth() : game.getResources().getDisplayMetrics().widthPixels;
        int height = streamView != null && streamView.getHeight() > 0 ? streamView.getHeight() : game.getResources().getDisplayMetrics().heightPixels;
        int[] location = new int[2];
        if (streamView != null) {
            streamView.getLocationOnScreen(location);
        }

        float size = Math.max(160f, Math.min(width, height) * 0.22f);
        float margin = Math.max(24f, Math.min(width, height) * 0.04f);
        float left = location[0] + width - size - margin;
        float top = location[1] + margin;
        return new RectF(left, top, left + size, top + size);
    }

    private void togglePortalEditMode() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager == null) return;
        if (portalManager.getPortalCount() == 0) {
            game.postNotification("请先添加画面映射", 2000);
            return;
        }
        int currentMode = portalManager.getCurrentEditMode();
        int nextMode = currentMode == 0 ? 1 : currentMode == 1 ? 2 : 0;
        portalManager.setEditingMode(nextMode);
        String notificationText = nextMode == 1
                ? getString(R.string.game_menu_portal_edit_source)
                : nextMode == 2
                ? getString(R.string.game_menu_portal_edit_target)
                : getString(R.string.game_menu_portal_exit_edit);
        game.postNotification(notificationText, 2000);
    }

    private void showPortalManagerDialog() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager == null) {
            Toast.makeText(game, "portalManager 为空", Toast.LENGTH_SHORT).show();
            return;
        }

        List<PortalConfig> portals = portalManager.getPortalConfigsSnapshot();
        if (portals.isEmpty()) {
            new android.app.AlertDialog.Builder(game)
                    .setTitle("管理画面映射")
                    .setMessage("还没有画面映射。请先添加一个画面映射，再调整源区域和显示位置。")
                    .setPositiveButton("添加画面映射", (dialog, which) -> addPortal())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        String[] items = new String[portals.size()];
        for (int i = 0; i < portals.size(); i++) {
            PortalConfig config = portals.get(i);
            String mode = config.editing
                    ? (config.editMode == 1 ? "编辑源区域" : "编辑目标区域")
                    : "未编辑";
            items[i] = config.name + " · " + (config.enabled ? "开启" : "关闭") + " · " + mode;
        }

        new android.app.AlertDialog.Builder(game)
                .setTitle("管理画面映射")
                .setItems(items, (dialog, which) -> showPortalActionsDialog(portals.get(which).id))
                .setPositiveButton("添加", (dialog, which) -> addPortal())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPortalActionsDialog(int portalId) {
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager == null) {
            return;
        }

        PortalConfig selected = null;
        for (PortalConfig config : portalManager.getPortalConfigsSnapshot()) {
            if (config.id == portalId) {
                selected = config;
                break;
            }
        }
        if (selected == null) {
            game.postNotification("画面映射已不存在", 2000);
            return;
        }

        String[] actions = new String[] {
                "编辑源区域",
                "编辑目标区域",
                selected.enabled ? "关闭画面映射" : "开启画面映射",
                "复制画面映射",
                "删除画面映射"
        };

        PortalConfig finalSelected = selected;
        new android.app.AlertDialog.Builder(game)
                .setTitle(finalSelected.name)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            portalManager.setPortalEditingMode(portalId, 1);
                            game.postNotification("正在编辑源区域", 2000);
                            break;
                        case 1:
                            portalManager.setPortalEditingMode(portalId, 2);
                            game.postNotification("正在编辑目标区域", 2000);
                            break;
                        case 2:
                            portalManager.setPortalEnabled(portalId, !finalSelected.enabled);
                            game.postNotification(finalSelected.enabled ? "已关闭画面映射" : "已开启画面映射", 2000);
                            break;
                        case 3:
                            PortalConfig duplicate = portalManager.duplicatePortal(portalId);
                            if (duplicate != null) {
                                portalManager.setPortalEditingMode(duplicate.id, 2);
                                game.postNotification("已复制画面映射，请调整目标区域", 2000);
                            }
                            break;
                        case 4:
                            confirmDeletePortal(portalId, finalSelected.name);
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeletePortal(int portalId, String portalName) {
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager == null) {
            return;
        }

        new android.app.AlertDialog.Builder(game)
                .setTitle("删除画面映射")
                .setMessage("确定删除 " + portalName + "？")
                .setPositiveButton("删除", (dialog, which) -> {
                    portalManager.removePortal(portalId);
                    game.postNotification("已删除画面映射", 2000);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 显示菜单动画
     */
    private void showMenuWithAnimation() {
        if (isMenuVisible) return;
        
        isMenuVisible = true;
        if (game != null) {
            game.updateSystemGestureExclusion(false);
        }
        
        // 确保菜单面板可见并开始动画
        menuPanel.setVisibility(View.VISIBLE);
        
        // 立即开始动画，避免任何延迟导致的闪烁
        float start = showFromLeft ? -menuPanel.getWidth() : menuPanel.getWidth();
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", start, 0);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    /**
     * 隐藏菜单动画
     */
    public void hideMenuWithAnimation() {
        android.util.Log.d("GameMenu", "hideMenuWithAnimation called, isMenuVisible: " + isMenuVisible);
        
        if (!isMenuVisible) {
            GameMenu.setMenuShowing(false);
            if (getFragmentManager() != null) {
                getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
            }
            return;
        }
        
        isMenuVisible = false;
        float end = showFromLeft ? -menuPanel.getWidth() : menuPanel.getWidth();
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", 0, end);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                android.util.Log.d("GameMenu", "Animation ended, removing fragment");
                // 更新菜单显示状态
                GameMenu.setMenuShowing(false);
                if (game != null) {
                    game.updateSystemGestureExclusion(true);
                }

                if (getFragmentManager() != null) {
                    getFragmentManager().beginTransaction().remove(GameMenuFragment.this).commitAllowingStateLoss();
                }
            }
        });
        animator.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        GameMenu.setMenuShowing(false);
        if (game != null) {
            game.updateSystemGestureExclusion(true);
        }
    }

    /**
     * 确保游戏窗口有焦点后执行操作
     */
    private void runWithGameFocus(Runnable runnable) {
        if (game.isFinishing()) {
            return;
        }
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), 10);
            return;
        }
        runnable.run();
    }

    /**
     * 启用输入法
     */
    private void enableKeyboard() {
        runWithGameFocus(game::toggleKeyboard);
    }

    /**
     * 发送键盘按键
     */
    private void sendKeys(short[] keys) {
        sendKeys(keys, 25);
    }

    private void runHotkeyWithDelay(short[] keys, int delay) {
        hideMenuWithAnimation();
        sendKeys(keys, delay);
    }

    private void sendKeys(short[] keys, int delayMs) {
        new Thread(() -> {
            final byte[] modifier = {(byte) 0};

            for (short key : keys) {
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
                modifier[0] |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
            }

            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];
                modifier[0] &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    /**
     * 获取剪贴板内容
     */
    public static CharSequence getClipboardContent(Game context, final int[] retryCount, final long[] retryDelay) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();

        if (clipData != null && clipData.getItemCount() > 0) {
            ClipData.Item item = clipData.getItemAt(0);
            return item.getText();
        } else if (retryCount[0] > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                retryCount[0]--;
                retryDelay[0] *= 2;
                getClipboardContent(context, retryCount, retryDelay);
            }, retryDelay[0]);
        }

        return null;
    }

    /**
     * 获取剪贴板内容为字符串
     */
    public static String getClipboardContentAsString(Game context, final int[] retryCount, final long[] retryDelay) {
        CharSequence charSequence = getClipboardContent(context, retryCount, retryDelay);
        return charSequence != null ? charSequence.toString() : "";
    }

    private void showSwitchDisplayDialog() {
        Toast.makeText(game, "正在获取屏幕列表...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final List<NvHTTP.DisplayInfo> rawDisplays = conn.getDisplays();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!canShowSwitchDisplayUi()) {
                        return;
                    }

                    if (rawDisplays == null || rawDisplays.isEmpty()) {
                        showFallbackSwitchDisplayDialog();
                        return;
                    }
                    final List<NvHTTP.DisplayInfo> displays = new ArrayList<>(rawDisplays);
                    boolean hasPhysical = false;
                    boolean hasVirtual = false;
                    for (NvHTTP.DisplayInfo info : displays) {
                        if (!isVirtualDisplayInfo(info)) {
                            hasPhysical = true;
                        } else {
                            hasVirtual = true;
                        }
                    }
                    
                    String cachedGuid = android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                            .getString("cached_physical_display_guid", "");
                            
                    if (!hasPhysical) {
                        displays.add(0, new NvHTTP.DisplayInfo("\\\\.\\DISPLAY1", "物理主显示器", cachedGuid));
                    }
                    if (!hasVirtual) {
                        displays.add(new NvHTTP.DisplayInfo("virtual_fallback", "虚拟显示器 (强制激活)", ""));
                    }
                    
                    // 获取当前正在串流的显示器配置
                    String currentConfigDisplay = android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                            .getString("last_stream_display_name", "");
                    boolean currentConfigUseVdd = android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                            .getBoolean("last_stream_display_use_vdd", false);
                    String currentConfigLabel = android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                            .getString(PREF_LAST_STREAM_DISPLAY_LABEL, "");
                    
                    NvHTTP.DisplayInfo currentInfo = null;
                    if (currentConfigDisplay != null && !currentConfigDisplay.trim().isEmpty()) {
                        for (NvHTTP.DisplayInfo info : displays) {
                            if (matchesDisplaySelection(info, currentConfigDisplay)) {
                                currentInfo = info;
                                break;
                            }
                        }
                    }
                    if (currentInfo == null && currentConfigUseVdd) {
                        for (NvHTTP.DisplayInfo info : displays) {
                            if (isVirtualDisplayInfo(info)) {
                                currentInfo = info;
                                break;
                            }
                        }
                    }
                    
                    String currentDisplayNameText;
                    String currentDeviceIdText;
                    if (currentInfo != null) {
                        currentDisplayNameText = getDisplayNickname(currentInfo);
                        currentDeviceIdText = getDisplayIdentifier(currentInfo);
                    } else {
                        if (currentConfigUseVdd) {
                            currentDisplayNameText = !isBlank(currentConfigLabel) ? currentConfigLabel : "虚拟显示器 (强制激活)";
                            currentDeviceIdText = "VDD";
                        } else if (currentConfigDisplay == null || currentConfigDisplay.trim().isEmpty()) {
                            currentDisplayNameText = !isBlank(currentConfigLabel) ? currentConfigLabel : "物理主屏幕";
                            currentDeviceIdText = (cachedGuid != null && !cachedGuid.trim().isEmpty()) ? cachedGuid : "\\\\.\\DISPLAY1";
                        } else {
                            currentDisplayNameText = !isBlank(currentConfigLabel) ? currentConfigLabel : "自定义显示器";
                            currentDeviceIdText = currentConfigDisplay;
                        }
                    }
                    
                    // 构建精美自定义 View
                    LinearLayout layout = new LinearLayout(game);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(dp(24), dp(16), dp(24), dp(12));
                    
                    TextView tvStatus = new TextView(game);
                    tvStatus.setText("当前串流：" + currentDisplayNameText + "\n设备 ID：" + currentDeviceIdText);
                    tvStatus.setTextColor(0xFFB0B0B0);
                    tvStatus.setTextSize(13);
                    tvStatus.setLineSpacing(0, 1.2f);
                    layout.addView(tvStatus);
                    
                    View divider = new View(game);
                    divider.setBackgroundColor(0xFF3E4A59);
                    LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                    dividerLp.topMargin = dp(12);
                    dividerLp.bottomMargin = dp(8);
                    layout.addView(divider, dividerLp);
                    
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(game);
                    builder.setTitle("切换显示器");
                    builder.setView(layout);
                    builder.setNegativeButton(android.R.string.cancel, null);
                    
                    final android.app.AlertDialog dialog = builder.create();
                    
                    int addedItems = 0;
                    for (NvHTTP.DisplayInfo selected : displays) {
                        // 过滤掉当前正在串流的显示器
                        boolean isCurrent = false;
                        if (currentConfigUseVdd && (currentConfigDisplay == null || currentConfigDisplay.trim().isEmpty())) {
                            isCurrent = isVirtualDisplayInfo(selected);
                        } else {
                            isCurrent = matchesDisplaySelection(selected, currentDeviceIdText);
                        }
                        
                        if (isCurrent) {
                            continue;
                        }
                        
                        TextView item = new TextView(game);
                        item.setText(getDisplayOptionLabel(selected));
                        item.setTextColor(0xFFFFFFFF);
                        item.setTextSize(15);
                        item.setPadding(dp(16), dp(14), dp(16), dp(14));
                        item.setClickable(true);
                        
                        TypedValue outValue = new TypedValue();
                        game.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                        item.setBackgroundResource(outValue.resourceId);
                        
                        item.setOnClickListener(v -> {
                            String targetValue = (selected.deviceId != null && !selected.deviceId.trim().isEmpty()) 
                                    ? selected.deviceId : selected.displayName;
                            
                            boolean isVirtual = isVirtualDisplayInfo(selected);
                            
                            if ("virtual_fallback".equals(selected.displayName)) {
                                targetValue = "";
                            }
                            
                            String toastText = getDisplayNickname(selected);
                            rememberDisplayLabel(toastText);
                            Toast.makeText(game, "正在切换到: " + toastText + "，请稍候...", Toast.LENGTH_SHORT).show();
                            game.recreateConnectionWithDisplay(targetValue, isVirtual);
                            dialog.dismiss();
                        });
                        
                        layout.addView(item);
                        addedItems++;
                    }
                    
                    if (addedItems == 0) {
                        TextView itemEmpty = new TextView(game);
                        itemEmpty.setText("无其他可用显示器");
                        itemEmpty.setTextColor(0xFF7D8797);
                        itemEmpty.setTextSize(14);
                        itemEmpty.setGravity(android.view.Gravity.CENTER);
                        itemEmpty.setPadding(dp(16), dp(16), dp(16), dp(16));
                        layout.addView(itemEmpty);
                    }
                    
                    dialog.show();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (canShowSwitchDisplayUi()) {
                        showFallbackSwitchDisplayDialog();
                    }
                });
            }
        }).start();
    }

    private void showFallbackSwitchDisplayDialog() {
        if (!canShowSwitchDisplayUi()) {
            return;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(game);
        builder.setTitle("切换显示器 (未能自动获取列表，请选择常用项)");
        
        final String[] items = new String[]{"物理主屏幕 (\\\\.\\DISPLAY1)", "虚拟显示器 (强制激活)", "手动输入名称..."};
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                String cachedGuid = android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                        .getString("cached_physical_display_guid", "");
                String targetDisplay = (cachedGuid != null && !cachedGuid.trim().isEmpty()) ? cachedGuid : "\\\\.\\DISPLAY1";
                rememberDisplayLabel("物理主屏幕");
                Toast.makeText(game, "正在切换到: " + targetDisplay + "，请稍候...", Toast.LENGTH_SHORT).show();
                game.recreateConnectionWithDisplay(targetDisplay, false);
            } else if (which == 1) {
                rememberDisplayLabel("虚拟显示器 (强制激活)");
                Toast.makeText(game, "正在激活并切换到虚拟显示器，请稍候...", Toast.LENGTH_SHORT).show();
                game.recreateConnectionWithDisplay("", true);
            } else {
                android.app.AlertDialog.Builder inputBuilder = new android.app.AlertDialog.Builder(game);
                inputBuilder.setTitle("输入显示器名称");
                final android.widget.EditText input = new android.widget.EditText(game);
                input.setHint("Windows 示例: \\\\.\\DISPLAY2\nLinux 示例: DP-1");
                inputBuilder.setView(input);
                inputBuilder.setPositiveButton("确定", (dialog1, which1) -> {
                    String customDisplay = input.getText().toString().trim();
                    if (!customDisplay.isEmpty()) {
                        boolean isVirtual = customDisplay.toLowerCase(java.util.Locale.ROOT).contains("zako")
                                || customDisplay.toLowerCase(java.util.Locale.ROOT).contains("virtual");
                        rememberDisplayLabel(customDisplay);
                        Toast.makeText(game, "正在切换到: " + customDisplay + "，请稍候...", Toast.LENGTH_SHORT).show();
                        game.recreateConnectionWithDisplay(customDisplay, isVirtual);
                    }
                });
                inputBuilder.setNegativeButton(android.R.string.cancel, null);
                inputBuilder.show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private boolean canShowSwitchDisplayUi() {
        return game != null && !game.isFinishing() && !game.isDestroyed();
    }

    private void rememberDisplayLabel(String label) {
        if (game == null || isBlank(label)) {
            return;
        }

        android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                .edit()
                .putString(PREF_LAST_STREAM_DISPLAY_LABEL, label.trim())
                .apply();
    }

    private String getDisplayNickname(NvHTTP.DisplayInfo info) {
        if (info == null) {
            return "未知显示器";
        }

        if ("virtual_fallback".equals(info.displayName)) {
            return "虚拟显示器 (强制激活)";
        }

        String friendlyName = normalizeDisplayName(info.friendlyName);
        String displayName = normalizeDisplayName(info.displayName);
        if (!friendlyName.isEmpty()) {
            return friendlyName;
        }

        if (isVirtualDisplayInfo(info)) {
            return "虚拟显示器";
        }

        return displayName.isEmpty() ? "物理显示器" : displayName;
    }

    private String getDisplayIdentifier(NvHTTP.DisplayInfo info) {
        if (info == null) {
            return "";
        }

        String deviceId = normalizeDisplayName(info.deviceId);
        if (!deviceId.isEmpty()) {
            return deviceId;
        }
        return normalizeDisplayName(info.displayName);
    }

    private String getDisplayOptionLabel(NvHTTP.DisplayInfo info) {
        return getDisplayNickname(info);
    }

    private boolean isVirtualDisplayInfo(NvHTTP.DisplayInfo info) {
        if (info == null) {
            return false;
        }

        String lowerName = safeLower(info.displayName);
        String lowerFriendly = safeLower(info.friendlyName);
        String lowerDeviceId = safeLower(info.deviceId);
        return lowerName.contains("zako") || lowerName.contains("virtual")
                || lowerFriendly.contains("zako") || lowerFriendly.contains("virtual")
                || lowerDeviceId.contains("zako") || lowerDeviceId.contains("virtual")
                || "virtual_fallback".equals(info.displayName);
    }

    private boolean matchesDisplaySelection(NvHTTP.DisplayInfo info, String selection) {
        if (info == null || selection == null) {
            return false;
        }

        String normalizedSelection = normalizeDisplayName(selection);
        return normalizedSelection.equals(normalizeDisplayName(info.displayName))
                || normalizedSelection.equals(normalizeDisplayName(info.deviceId))
                || normalizedSelection.equals(normalizeDisplayName(info.toString()));
    }

    private String normalizeDisplayName(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        int friendlySuffixIndex = normalized.indexOf(" (");
        if (friendlySuffixIndex >= 0) {
            normalized = normalized.substring(0, friendlySuffixIndex).trim();
        }
        return normalized;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
} 
