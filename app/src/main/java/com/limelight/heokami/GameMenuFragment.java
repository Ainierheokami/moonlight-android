package com.limelight.heokami;

import com.limelight.utils.AppExecutors;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import com.limelight.utils.AppToast;

import android.app.Activity;
import android.app.Fragment;

import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.content.res.ColorStateList;
import android.util.DisplayMetrics;
import androidx.core.content.ContextCompat;
import com.limelight.utils.MaxHeightScrollView;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.limelight.Game;
import com.limelight.heokami.layout.LayoutProfileDialogs;
import com.limelight.heokami.layout.LayoutProfileManager;
import com.limelight.heokami.layout.LayoutProfileRepository;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.heokami.FloatingVirtualKeyboardFragment;
import com.limelight.utils.OverlayAlertDialog;
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
    private static final String PREF_GAME_MENU_SECTION_ORDER = "game_menu_section_order";

    private GameMenuHost host;
    // Activity used for views, strings, dialogs and toasts: the stream, or the test-mode screen.
    private Activity game;
    // The stream itself; null when the menu is shown by the settings test mode.
    private Game realGame;
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
    private ItemTouchHelper sectionOrderTouchHelper;

    // 悬浮功能盒子：屏幕横向有足够空间时放在面板对侧，用 tab 切换功能区；否则 tab 放进面板里
    private static final String PREF_LAST_BOX_TAB = "game_menu_last_box_tab";
    private static final String PREF_LAST_PANEL_TAB = "game_menu_last_panel_tab";
    private View menuBox;
    private LinearLayout boxTabBar;
    private LinearLayout boxContent;
    private MaxHeightScrollView boxScroll;
    private LinearLayout panelTabBar;
    private View panelTabScroll;
    private boolean boxMode = false;
    private int boxWidthPx = 0;
    private MenuTab selectedBoxTab = MenuTab.STREAM;
    private MenuTab selectedPanelTab = MenuTab.INPUT;

    /** A tab groups menu sections; the same tabs are used in the box and (portrait) in the panel. */
    private enum MenuTab {
        INPUT(R.string.menu_tab_input, GameMenuSection.INPUT),
        STREAM(R.string.menu_tab_stream, GameMenuSection.STREAM),
        HOTKEYS(R.string.menu_tab_hotkeys, GameMenuSection.HOTKEYS, GameMenuSection.CUSTOM),
        LAYOUT(R.string.menu_tab_layout, GameMenuSection.LAYOUT),
        TOOLS(R.string.menu_tab_tools, GameMenuSection.OVERLAY, GameMenuSection.PORTALS);

        final int titleRes;
        final GameMenuSection[] sections;

        MenuTab(int titleRes, GameMenuSection... sections) {
            this.titleRes = titleRes;
            this.sections = sections;
        }

        boolean contains(GameMenuSection section) {
            for (GameMenuSection s : sections) {
                if (s == section) return true;
            }
            return false;
        }
    }

    private interface TabSelectedListener {
        void onSelected(MenuTab tab);
    }
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
        return newInstance((GameMenuHost) game, showFromLeft);
    }

    public static GameMenuFragment newInstance(GameMenuHost host, boolean showFromLeft) {
        GameMenuFragment fragment = new GameMenuFragment();
        fragment.host = host;
        fragment.game = host.menuActivity();
        fragment.realGame = host.getStreamGame();
        fragment.conn = host.getMenuConnection();
        fragment.showFromLeft = showFromLeft;
        return fragment;
    }

    private boolean isDemo() {
        return host != null && host.isMenuDemo();
    }

    /** In the settings test mode actions that need a stream only explain themselves. */
    private void showDemoNotice() {
        AppToast.makeText(game, R.string.game_menu_demo_notice, AppToast.LENGTH_SHORT).show();
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
        menuBox = view.findViewById(R.id.menu_box);
        boxTabBar = view.findViewById(R.id.box_tab_bar);
        boxContent = view.findViewById(R.id.box_content);
        boxScroll = view.findViewById(R.id.box_scroll);
        panelTabBar = view.findViewById(R.id.panel_tab_bar);
        panelTabScroll = view.findViewById(R.id.panel_tab_scroll);
        computeLayoutMode();
        selectedBoxTab = loadTab(PREF_LAST_BOX_TAB, MenuTab.STREAM, MenuTab.INPUT);
        selectedPanelTab = loadTab(PREF_LAST_PANEL_TAB, MenuTab.INPUT, null);
        renderStatusBar();
        renderDashboard();
        renderTouchModeOptions();
    }

    /** Decides whether the screen has room for the tool box next to the panel. */
    private void computeLayoutMode() {
        DisplayMetrics dm = game.getResources().getDisplayMetrics();
        int panelWidth = GameMenuGeometry.panelWidthPx(dm.widthPixels, dm.heightPixels, dm.density);
        boxWidthPx = GameMenuGeometry.toolBoxWidthPx(dm.widthPixels, dm.heightPixels, dm.density, panelWidth);
        boxMode = boxWidthPx > 0 && menuBox != null;
    }

    private MenuTab loadTab(String key, MenuTab fallback, MenuTab excluded) {
        String saved = PreferenceManager.getDefaultSharedPreferences(game).getString(key, null);
        if (saved != null) {
            try {
                MenuTab tab = MenuTab.valueOf(saved);
                if (tab != excluded) return tab;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }

    private void saveTab(String key, MenuTab tab) {
        PreferenceManager.getDefaultSharedPreferences(game).edit().putString(key, tab.name()).apply();
    }

    private int color(int resId) {
        return ContextCompat.getColor(game, resId);
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
        
        int menuWidth = GameMenuGeometry.panelWidthPx(screenWidth, screenHeight,
                getResources().getDisplayMetrics().density);
        int maxWidthPx = (int) (480 * getResources().getDisplayMetrics().density);
        
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

        setupToolBox(screenHeight);
    }

    /** Puts the tool box on the side opposite the panel, sized by {@link GameMenuGeometry}. */
    private void setupToolBox(int screenHeight) {
        if (menuBox == null) return;
        if (!boxMode) {
            menuBox.setVisibility(View.GONE);
            return;
        }
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                boxWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_VERTICAL);
        int margin = dp(12);
        if (showFromLeft) {
            // panel on the left, box on the right
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.setMarginEnd(margin);
            params.rightMargin = margin;
            menuBox.setTranslationX(boxWidthPx + margin);
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_START);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.setMarginStart(margin);
            params.leftMargin = margin;
            menuBox.setTranslationX(-(boxWidthPx + margin));
        }
        menuBox.setLayoutParams(params);
        // keep the box inside the screen: tab bar and card padding take the rest
        boxScroll.setMaxHeightPx((int) (screenHeight * 0.86f) - dp(24 + 36 + 8 + 24));
        menuBox.setVisibility(View.INVISIBLE);
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
        String quality = game.getString(R.string.menu_unknown);
        if (host.getPrefConfig() != null) {
            quality = game.getString(R.string.menu_status_quality_fps,
                    host.getPrefConfig().width + "x" + host.getPrefConfig().height, host.getPrefConfig().fps);
        }
        addStatusChip(game.getString(R.string.menu_status_quality), quality);
        
        // 3. 视频码率
        String bitrate = game.getString(R.string.menu_unknown);
        if (conn != null) {
            bitrate = String.format(java.util.Locale.getDefault(), "%.1f Mbps", conn.getCurrentBitrate() / 1000f);
        } else if (host.getPrefConfig() != null) {
            bitrate = String.format(java.util.Locale.getDefault(), "%.1f Mbps", host.getPrefConfig().bitrate / 1000f);
        }
        addStatusChip(game.getString(R.string.menu_status_bitrate), bitrate);

        // 4. 串流音量（客户端播放增益，按主机保存）
        addStatusChip(getString(R.string.game_menu_audio_volume_short), host.getStreamAudioGainLabel());
        
        // 5. 当前时间
        String currentTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
        addStatusChip(game.getString(R.string.menu_status_time), currentTime);
    }

    private void addStatusChip(String label, String value) {
        TextView chip = new TextView(game);
        chip.setText(label + "\n" + value);
        chip.setTextColor(color(R.color.menu_text_secondary));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setMaxLines(2);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setIncludeFontPadding(false);
        chip.setLineSpacing(0, 0.92f);
        chip.setBackgroundResource(R.drawable.menu_panel_background);
        chip.setPadding(dp(5), dp(5), dp(5), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        statusContainer.addView(chip, lp);
    }

    private void renderDashboard() {
        if (dashboardContainer == null) return;
        dashboardContainer.removeAllViews();

        List<GameMenuAction> actions = buildMenuActions();
        if (boxMode) {
            // 面板只保留输入控制，其余功能区放进对侧的 tab 盒子
            panelTabScroll.setVisibility(View.GONE);
            addSections(dashboardContainer, actions, MenuTab.INPUT);
            renderBox(actions);
        } else {
            // 竖屏等空间不足：tab 放进面板
            panelTabScroll.setVisibility(View.VISIBLE);
            renderTabBar(panelTabBar, MenuTab.values(), selectedPanelTab, tab -> {
                selectedPanelTab = tab;
                saveTab(PREF_LAST_PANEL_TAB, tab);
                renderDashboard();
            });
            renderTabContent(dashboardContainer, actions, selectedPanelTab);
        }
    }

    private void renderBox(List<GameMenuAction> actions) {
        if (boxContent == null) return;
        MenuTab[] tabs = {MenuTab.STREAM, MenuTab.HOTKEYS, MenuTab.LAYOUT, MenuTab.TOOLS};
        if (selectedBoxTab == MenuTab.INPUT) selectedBoxTab = MenuTab.STREAM;
        renderTabBar(boxTabBar, tabs, selectedBoxTab, tab -> {
            selectedBoxTab = tab;
            saveTab(PREF_LAST_BOX_TAB, tab);
            renderBox(buildMenuActions());
        });
        renderTabContent(boxContent, actions, selectedBoxTab);
    }

    private void renderTabContent(LinearLayout container, List<GameMenuAction> actions, MenuTab tab) {
        container.removeAllViews();
        if (tab == MenuTab.LAYOUT) {
            container.addView(createLayoutStrip());
        }
        addSections(container, actions, tab);
    }

    private void addSections(LinearLayout container, List<GameMenuAction> actions, MenuTab tab) {
        for (GameMenuSection section : getOrderedSections()) {
            if (!tab.contains(section)) continue;
            List<GameMenuAction> sectionActions = new ArrayList<>();
            for (GameMenuAction action : actions) {
                if (action.visible && action.section == section) {
                    sectionActions.add(action);
                }
            }
            if (sectionActions.isEmpty()) continue;
            java.util.Collections.sort(sectionActions, (a, b) -> Integer.compare(a.priority, b.priority));
            addSection(container, section, sectionActions);
        }
    }

    private void renderTabBar(LinearLayout bar, MenuTab[] tabs, MenuTab selected, TabSelectedListener listener) {
        bar.removeAllViews();
        for (MenuTab tab : tabs) {
            final boolean isSelected = tab == selected;
            TextView pill = createPill(getString(tab.titleRes), isSelected);
            pill.setOnClickListener(v -> {
                if (!isSelected) listener.onSelected(tab);
            });
            bar.addView(pill, pillParams());
        }
    }

    /** A rounded tab/chip. Selected ones use the accent colour. */
    private TextView createPill(CharSequence text, boolean selected) {
        TextView pill = new TextView(game);
        pill.setText(text);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pill.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        pill.setTextColor(color(selected ? R.color.menu_text_primary : R.color.menu_text_secondary));
        pill.setGravity(android.view.Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(dp(16), 0, dp(16), 0);
        pill.setBackgroundResource(selected ? R.drawable.menu_tab_selected : R.drawable.menu_tab_normal);
        pill.setClickable(true);
        return pill;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(0, 0, dp(8), 0);
        return lp;
    }

    /** The layout tab's header: the available keyboard layouts as chips, tap one to switch. */
    private View createLayoutStrip() {
        LinearLayout wrap = new LinearLayout(game);
        wrap.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(game);
        title.setText(R.string.game_menu_layout_strip_title);
        title.setTextColor(color(R.color.menu_text_muted));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(6));
        wrap.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(game);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(game);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);
        wrap.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutProfileRepository repo = LayoutProfileManager.INSTANCE.get(game);
        String activeId = repo.getActiveId();
        for (LayoutProfileRepository.Profile profile : repo.list()) {
            TextView chip = createPill(profile.name, profile.id.equals(activeId));
            chip.setOnClickListener(v -> switchKeyboardLayout(profile.id));
            row.addView(chip, pillParams());
        }
        TextView add = createPill("\uFF0B", false);
        add.setOnClickListener(v -> {
            hideMenuWithAnimation();
            LayoutProfileDialogs.showNew(game, host.createKeyboardLayoutContext());
        });
        row.addView(add, pillParams());
        return wrap;
    }

    private void switchKeyboardLayout(String id) {
        if (LayoutProfileManager.INSTANCE.switchTo(game, id)) {
            host.reloadVirtualKeyboardLayout();
            renderDashboard();
        }
    }

    private List<GameMenuSlider> buildMenuSliders(GameMenuSection section) {
        List<GameMenuSlider> sliders = new ArrayList<>();
        if (section == GameMenuSection.STREAM) {
            sliders.add(new GameMenuSlider(
                    R.string.game_menu_audio_volume,
                    Game.STREAM_AUDIO_GAIN_MIN_PERCENT,
                    Game.STREAM_AUDIO_GAIN_MAX_PERCENT,
                    10,
                    Game.STREAM_AUDIO_GAIN_DEFAULT_PERCENT,
                    host.getStreamAudioGainPercent(),
                    value -> {
                        host.setStreamAudioGainPercent(value);
                        renderStatusBar();
                    }));
        }
        else if (section == GameMenuSection.INPUT) {
            sliders.add(new GameMenuSlider(
                    R.string.game_menu_touchpad_sensitivity,
                    10,
                    300,
                    5,
                    PreferenceConfiguration.DEFAULT_TOUCHPAD_SENSITIVITY,
                    host.getTouchpadSensitivityPercent(),
                    value -> {
                        host.setTouchpadSensitivityPercent(value);
                        renderStatusBar();
                    }));
        }
        return sliders;
    }

    private List<GameMenuAction> buildMenuActions() {
        List<GameMenuAction> actions = new ArrayList<>();
        actions.add(new GameMenuAction("bitrate", R.string.game_menu_adjust_bitrate_short, 0, GameMenuSection.STREAM, 10, false, true, true, v -> {
            hideMenuWithAnimation();
            StreamBitrateMenu.show(realGame, conn);
        }));
        actions.add(new GameMenuAction("presets", R.string.game_menu_stream_presets_short, 0, GameMenuSection.STREAM, 30, false, true, true, v -> {
            hideMenuWithAnimation();
            StreamPresetMenu.show(realGame, conn);
        }));
        actions.add(new GameMenuAction("stream_enhance", R.string.game_menu_stream_enhance, 0, GameMenuSection.STREAM, 40, false, true, true, v -> {
            hideMenuWithAnimation();
            StreamEnhanceMenu.show(realGame, conn);
        }));
        actions.add(new GameMenuAction("switch_display", 0, 0, GameMenuSection.STREAM, 50, false, true, true, v -> {
            hideMenuWithAnimation();
            showSwitchDisplayDialog();
        }, game.getString(R.string.menu_switch_display_title)));

        actions.add(new GameMenuAction("ime", R.string.game_menu_enable_keyboard, R.drawable.ic_keyboard, GameMenuSection.INPUT, 10, false, true, true, v -> {
            hideMenuWithAnimation();
            enableKeyboard();
        }));
        actions.add(new GameMenuAction("floating_keyboard", R.string.game_menu_floating_keyboard, R.drawable.ic_floating_keyboard, GameMenuSection.INPUT, 20, false, true, true, v -> {
            hideMenuWithAnimation();
            try {
                FloatingVirtualKeyboardFragment.Companion.show(realGame);
            } catch (Exception e) {
                Log.e("GameMenuFragment", "Error showing floating keyboard", e);
            }
        }));
        actions.add(new GameMenuAction("full_keyboard", R.string.game_menu_full_keyboard, R.drawable.ic_full_keyboard, GameMenuSection.INPUT, 30, false, true, true, v -> {
            hideMenuWithAnimation();
            VirtualKeyboardDialogFragment.show(realGame);
        }));
        actions.add(new GameMenuAction("send_clipboard", R.string.game_menu_send_clipboard_content, R.drawable.ic_clipboard, GameMenuSection.INPUT, 40, false, true, true, v -> {
            hideMenuWithAnimation();
            conn.sendUtf8Text(getClipboardContentAsString(realGame, new int[]{3}, new long[]{30}));
        }));

        actions.add(new GameMenuAction("copy", R.string.game_menu_copy, R.drawable.ic_copy, GameMenuSection.HOTKEYS, 10, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_C.getCode()})));
        actions.add(new GameMenuAction("paste", R.string.game_menu_paste, R.drawable.ic_paste, GameMenuSection.HOTKEYS, 20, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_V.getCode()})));
        actions.add(new GameMenuAction("screen_keyboard", R.string.game_menu_virtual_keyboard_short, R.drawable.ic_keyboard, GameMenuSection.HOTKEYS, 30, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_O.getCode()})));
        actions.add(new GameMenuAction("alt_tab", R.string.game_menu_switch_window_short, R.drawable.ic_switch_window, GameMenuSection.HOTKEYS, 40, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_TAB.getCode()})));
        actions.add(new GameMenuAction("home", R.string.game_menu_hotkey_home, R.drawable.ic_home, GameMenuSection.HOTKEYS, 50, false, true, true, v -> runHotkey(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_D.getCode()})));

        actions.add(new GameMenuAction("controller", R.string.game_menu_toggle_virtual_controller, 0, GameMenuSection.OVERLAY, 10, false, true, true, v -> {
            hideMenuWithAnimation();
            host.toggleVirtualController();
        }));
        actions.add(new GameMenuAction("virtual_keyboard", R.string.game_menu_toggle_virtual_keyboard, 0, GameMenuSection.LAYOUT, 20, false, true, true, v -> {
            hideMenuWithAnimation();
            host.toggleVirtualKeyboard();
            AppToast.makeText(game, game.getString(R.string.game_menu_toggle_virtual_keyboard_toast), AppToast.LENGTH_SHORT).show();
        }));
        actions.add(new GameMenuAction("keyboard_layout", 0, 0, GameMenuSection.LAYOUT, 30, false, true, true, v -> {
            hideMenuWithAnimation();
            LayoutProfileDialogs.show(game, host.createKeyboardLayoutContext());
        }, game.getString(R.string.game_menu_manage_layouts)));
        actions.add(new GameMenuAction("edit_virtual_keyboard", R.string.game_menu_edit_virtual_keyboard, 0, GameMenuSection.LAYOUT, 10, false, true, true, v -> openVirtualKeyboardEditor()));
        actions.add(new GameMenuAction("perf", R.string.game_menu_toggle_perf_overlay, 0, GameMenuSection.OVERLAY, 40, false, true, true, v -> {
            hideMenuWithAnimation();
            host.togglePerfOverlay();
        }));

        actions.add(new GameMenuAction("portal_toggle", host.arePortalsEnabled() ? R.string.game_menu_portal_disable : R.string.game_menu_portal_enable, 0, GameMenuSection.PORTALS, 10, false, true, host.getPortalManagerView() != null, v -> togglePortals()));
        actions.add(new GameMenuAction("portal_add", R.string.game_menu_portal_add, 0, GameMenuSection.PORTALS, 20, false, true, host.getPortalManagerView() != null, v -> addPortal()));
        actions.add(new GameMenuAction("portal_edit", R.string.game_menu_portal_toggle_edit, 0, GameMenuSection.PORTALS, 30, false, true, host.getPortalManagerView() != null, v -> togglePortalEditMode()));
        actions.add(new GameMenuAction("portal_manage", R.string.game_menu_portal_manage, 0, GameMenuSection.PORTALS, 40, false, true, host.getPortalManagerView() != null, v -> showPortalManagerDialog()));

        actions.add(new GameMenuAction("section_order", R.string.game_menu_section_order, 0, GameMenuSection.CUSTOM, 5, false, true, true, v -> showSectionOrderDialog()));
        actions.add(new GameMenuAction("edit_hotkeys", R.string.game_menu_edit_hotkeys, 0, GameMenuSection.CUSTOM, 10, false, true, true, v -> openCustomHotkeyManager()));
        List<CustomHotkeysManager.CustomHotkey> customItems = CustomHotkeysManager.load(game);
        int priority = 20;
        for (CustomHotkeysManager.CustomHotkey item : customItems) {
            actions.add(new GameMenuAction("custom_" + item.name, 0, 0, GameMenuSection.CUSTOM, priority++, false, true, true, v -> runCustomHotkey(item), item.name));
        }
        return actions;
    }

    private void addSection(LinearLayout container, GameMenuSection section, List<GameMenuAction> actions) {
        TextView title = new TextView(game);
        title.setText(section.titleRes);
        title.setTextColor(color(R.color.menu_text_muted));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(4));
        container.addView(title);

        for (GameMenuSlider slider : buildMenuSliders(section)) {
            container.addView(createSliderRow(slider));
        }

        GridLayout grid = new GridLayout(game);
        grid.setColumnCount(2);
        container.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (GameMenuAction action : actions) {
            grid.addView(createActionButton(action));
        }
    }

    private View createSliderRow(GameMenuSlider slider) {
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
        label.setTextColor(color(R.color.menu_text_primary));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(label, labelParams);

        SeekBar seekBar = new SeekBar(game);
        ColorStateList accent = ColorStateList.valueOf(color(R.color.menu_accent));
        seekBar.setProgressTintList(accent);
        seekBar.setThumbTintList(accent);
        seekBar.setMax((slider.max - slider.min) / slider.step);
        seekBar.setProgress(valueToProgress(slider, slider.currentValue));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(seekBar, seekParams);

        Button resetButton = new Button(game);
        resetButton.setAllCaps(false);
        resetButton.setText(R.string.game_menu_slider_reset);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        resetButton.setTextColor(color(R.color.menu_text_primary));
        resetButton.setPadding(0, 0, 0, 0);
        resetButton.setBackgroundResource(R.drawable.button_background_dark);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(42), dp(34));
        resetParams.setMargins(dp(6), 0, 0, 0);
        row.addView(resetButton, resetParams);

        final int[] currentValue = new int[]{progressToValue(slider, seekBar.getProgress())};
        updateSliderLabel(label, slider, currentValue[0]);
        View.OnClickListener editSliderValue = v -> showSliderValueDialog(slider, seekBar, label, currentValue);
        label.setOnClickListener(editSliderValue);
        label.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            label.setForeground(getSelectableItemBackground());
        }
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

    private int valueToProgress(GameMenuSlider slider, int value) {
        int clampedValue = Math.max(slider.min, Math.min(slider.max, value));
        return (clampedValue - slider.min) / slider.step;
    }

    private int progressToValue(GameMenuSlider slider, int progress) {
        return Math.max(slider.min, Math.min(slider.max, slider.min + progress * slider.step));
    }

    private void updateSliderLabel(TextView label, GameMenuSlider slider, int value) {
        label.setText(getString(slider.titleRes) + "\n" + value + "%");
    }

    private Drawable getSelectableItemBackground() {
        TypedValue outValue = new TypedValue();
        game.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return game.getResources().getDrawable(outValue.resourceId);
    }

    private void showSliderValueDialog(GameMenuSlider slider, SeekBar seekBar, TextView label, int[] currentValue) {
        EditText input = new EditText(game);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(currentValue[0]));
        input.setHint(slider.min + " - " + slider.max);
        int padding = dp(18);
        input.setPadding(padding, dp(8), padding, dp(8));

        LinearLayout container = new LinearLayout(game);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(8), dp(20), 0);

        TextView message = new TextView(game);
        message.setText(getString(R.string.game_menu_slider_input_message, slider.min, slider.max, slider.step));
        message.setTextColor(0xFF555555);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        message.setPadding(0, 0, 0, dp(8));
        container.addView(message);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new OverlayAlertDialog.Builder(game)
                .setTitle(getString(slider.titleRes))
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int value;
                    try {
                        value = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        AppToast.makeText(game, R.string.seekbar_input_number_error, AppToast.LENGTH_SHORT).show();
                        return;
                    }
                    value = normalizeSliderValue(slider, value);
                    seekBar.setProgress(valueToProgress(slider, value));
                    currentValue[0] = value;
                    updateSliderLabel(label, slider, value);
                    slider.applyCallback.apply(value);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private int normalizeSliderValue(GameMenuSlider slider, int value) {
        int clamped = Math.max(slider.min, Math.min(slider.max, value));
        int offset = clamped - slider.min;
        int roundedSteps = Math.round(offset / (float) slider.step);
        return Math.max(slider.min, Math.min(slider.max, slider.min + roundedSteps * slider.step));
    }

    private List<GameMenuSection> getOrderedSections() {
        List<GameMenuSection> ordered = new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(game);
        String savedOrder = prefs.getString(PREF_GAME_MENU_SECTION_ORDER, null);
        if (savedOrder != null) {
            String[] names = savedOrder.split(",");
            for (String name : names) {
                try {
                    GameMenuSection section = GameMenuSection.valueOf(name);
                    if (!ordered.contains(section)) {
                        ordered.add(section);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (GameMenuSection section : GameMenuSection.values()) {
            if (!ordered.contains(section)) {
                ordered.add(section);
            }
        }
        return ordered;
    }

    private void saveSectionOrder(List<GameMenuSection> sections) {
        StringBuilder builder = new StringBuilder();
        for (GameMenuSection section : sections) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(section.name());
        }
        PreferenceManager.getDefaultSharedPreferences(game)
                .edit()
                .putString(PREF_GAME_MENU_SECTION_ORDER, builder.toString())
                .apply();
    }

    private void resetSectionOrder() {
        PreferenceManager.getDefaultSharedPreferences(game)
                .edit()
                .remove(PREF_GAME_MENU_SECTION_ORDER)
                .apply();
    }

    private void showSectionOrderDialog() {
        List<GameMenuSection> sections = new ArrayList<>(getOrderedSections());
        View dialogView = LayoutInflater.from(game).inflate(R.layout.dialog_section_order, null);
        TextView subtitle = dialogView.findViewById(R.id.sectionOrderSubtitle);
        RecyclerView recyclerView = dialogView.findViewById(R.id.sectionOrderList);
        Button resetButton = dialogView.findViewById(R.id.sectionOrderResetButton);
        Button doneButton = dialogView.findViewById(R.id.sectionOrderDoneButton);

        subtitle.setText(R.string.game_menu_section_order_hint);

        recyclerView.setLayoutManager(new LinearLayoutManager(game));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(2), 0, dp(2));

        SectionOrderAdapter adapter = new SectionOrderAdapter(sections);
        recyclerView.setAdapter(adapter);
        sectionOrderTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                adapter.moveSection(from, to);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        sectionOrderTouchHelper.attachToRecyclerView(recyclerView);

        final OverlayAlertDialog dialog = new OverlayAlertDialog.Builder(game)
                .setView(dialogView)
                .create();
        dialog.setOnDismissListener(ignored -> sectionOrderTouchHelper = null);

        resetButton.setOnClickListener(v -> {
            resetSectionOrder();
            sections.clear();
            sections.addAll(getOrderedSections());
            adapter.notifyDataSetChanged();
        });
        doneButton.setOnClickListener(v -> {
            saveSectionOrder(sections);
            renderDashboard();
            dialog.dismiss();
        });

        dialog.show();
    }

    public static List<String> debugSectionOrderTitles() {
        List<String> titles = new ArrayList<>();
        for (GameMenuSection section : GameMenuSection.values()) {
            titles.add(section.name());
        }
        return titles;
    }

    private final class SectionOrderAdapter extends RecyclerView.Adapter<SectionOrderAdapter.SectionViewHolder> {
        private final List<GameMenuSection> sections;

        SectionOrderAdapter(List<GameMenuSection> sections) {
            this.sections = sections;
        }

        @Override
        public SectionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(game).inflate(R.layout.dialog_section_order_item, parent, false);
            return new SectionViewHolder(row);
        }

        @Override
        public void onBindViewHolder(SectionViewHolder holder, int position) {
            GameMenuSection section = sections.get(position);
            holder.titleView.setText(getString(section.titleRes));
            holder.subtitleView.setText(position == 0
                    ? getString(R.string.game_menu_section_order_first)
                    : getString(R.string.game_menu_section_order_drag_hint));
            holder.badgeView.setText(String.valueOf(position + 1));
            holder.handleView.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && sectionOrderTouchHelper != null) {
                    sectionOrderTouchHelper.startDrag(holder);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return sections.size();
        }

        private void moveSection(int from, int to) {
            if (from < 0 || to < 0 || from >= sections.size() || to >= sections.size() || from == to) {
                return;
            }
            GameMenuSection section = sections.remove(from);
            sections.add(to, section);
            notifyItemMoved(from, to);
            notifyItemRangeChanged(Math.min(from, to), Math.abs(from - to) + 1);
        }

        private final class SectionViewHolder extends RecyclerView.ViewHolder {
            final TextView badgeView;
            final TextView titleView;
            final TextView subtitleView;
            final ImageView handleView;

            SectionViewHolder(View itemView) {
                super(itemView);
                badgeView = itemView.findViewById(R.id.sectionOrderBadge);
                titleView = itemView.findViewById(R.id.sectionOrderTitleText);
                subtitleView = itemView.findViewById(R.id.sectionOrderSubtitleText);
                handleView = itemView.findViewById(R.id.sectionOrderHandle);
            }
        }
    }

    private Button createActionButton(GameMenuAction action) {
        Button button = new Button(game);
        button.setAllCaps(false);
        button.setText(action.overrideTitle != null ? action.overrideTitle : getString(action.titleRes));
        button.setTextColor(color(action.enabled ? R.color.menu_text_primary : R.color.menu_text_muted));
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
        if (isDemo() && !"section_order".equals(action.id)) {
            button.setOnClickListener(v -> showDemoNotice());
        } else {
            button.setOnClickListener(action.onClick);
        }
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
        VirtualKeyboard vk = host.getVirtualKeyboard();
        if (vk != null) {
            vk.show();
            vk.enterEditMode();
            new Handler(Looper.getMainLooper()).postDelayed(() -> new EditMenu(realGame, vk), ANIMATION_DURATION + 50);
        } else {
            AppToast.makeText(game, game.getString(R.string.menu_vk_not_ready_enter_edit), AppToast.LENGTH_SHORT).show();
        }
    }

    private void openCustomHotkeyManager() {
        hideMenuWithAnimation();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            VirtualKeyboard vk = host.getVirtualKeyboard();
            if (vk == null) {
                AppToast.makeText(game, game.getString(R.string.menu_vk_not_ready_edit), AppToast.LENGTH_SHORT).show();
                return;
            }
            CustomHotkeysManager.showManageDialog(realGame, vk, this::renderDashboard);
        }, ANIMATION_DURATION + 50);
    }

    private void runCustomHotkey(CustomHotkeysManager.CustomHotkey item) {
        hideMenuWithAnimation();
        VirtualKeyboard vk = host.getVirtualKeyboard();
        if (vk == null) {
            AppToast.makeText(game, game.getString(R.string.menu_vk_not_ready_run), AppToast.LENGTH_SHORT).show();
            return;
        }
        CustomHotkeysManager.runCustomHotkey(realGame, vk, item);
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
                                    host.quitAndDisconnect();
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
                        String quitText = game.getString(R.string.menu_quit_stream);
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
        int mode = host.getCurrentTouchMode();
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
        int mode = host.getCurrentTouchMode();
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
        button.setBackgroundResource(host.getCurrentTouchMode() == mode ? R.drawable.menu_header_background : R.drawable.button_background_dark);
        button.setOnClickListener(v -> {
            host.changeTouchMode(mode);
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
        AppToast.makeText(game, R.string.game_menu_disconnect_confirm, AppToast.LENGTH_SHORT).show();
    }

    private void togglePortals() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = host.getPortalManagerView();
        if (portalManager != null) {
            boolean enabled = portalManager.togglePortalsEnabled();
            host.postNotification(enabled ? getString(R.string.game_menu_portal_enable) : getString(R.string.game_menu_portal_disable), 2000);
        } else {
            AppToast.makeText(game, game.getString(R.string.portal_manager_null), AppToast.LENGTH_SHORT).show();
        }
    }

    private void addPortal() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = host.getPortalManagerView();
        if (portalManager == null) {
            AppToast.makeText(game, game.getString(R.string.portal_manager_null), AppToast.LENGTH_SHORT).show();
            return;
        }
        PortalConfig config = new PortalConfig();
        config.id = portalManager.generateNewId();
        config.srcRect = new RectF(0.2f, 0.2f, 0.4f, 0.4f);
        config.dstRect = createDefaultPortalTargetRect();
        config.enabled = true;
        config.name = game.getString(R.string.portal_default_name, config.id);
        if (!portalManager.arePortalsEnabled()) {
            portalManager.setPortalsEnabled(true);
        }
        portalManager.addPortal(config);
        portalManager.setPortalEditingMode(config.id, 1);
        host.postNotification(game.getString(R.string.portal_added_adjust_source), 2000);
    }

    private RectF createDefaultPortalTargetRect() {
        View streamView = host.getStreamView();
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
        PortalManagerView portalManager = host.getPortalManagerView();
        if (portalManager == null) return;
        if (portalManager.getPortalCount() == 0) {
            host.postNotification(game.getString(R.string.portal_add_first), 2000);
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
        host.postNotification(notificationText, 2000);
    }

    private void showPortalManagerDialog() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = host.getPortalManagerView();
        if (portalManager == null) {
            AppToast.makeText(game, game.getString(R.string.portal_manager_null), AppToast.LENGTH_SHORT).show();
            return;
        }

        List<PortalConfig> portals = portalManager.getPortalConfigsSnapshot();
        if (portals.isEmpty()) {
            new OverlayAlertDialog.Builder(game)
                    .setTitle(game.getString(R.string.portal_manage_title))
                    .setMessage(game.getString(R.string.portal_none_message))
                    .setPositiveButton(game.getString(R.string.portal_add_full), (dialog, which) -> addPortal())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        String[] items = new String[portals.size()];
        for (int i = 0; i < portals.size(); i++) {
            PortalConfig config = portals.get(i);
            String mode = config.editing
                    ? (config.editMode == 1 ? game.getString(R.string.portal_edit_source) : game.getString(R.string.portal_edit_target))
                    : game.getString(R.string.portal_not_editing);
            items[i] = config.name + " · " + (config.enabled ? game.getString(R.string.portal_on) : game.getString(R.string.portal_off)) + " · " + mode;
        }

        new OverlayAlertDialog.Builder(game)
                .setTitle(game.getString(R.string.portal_manage_title))
                .setItems(items, (dialog, which) -> showPortalActionsDialog(portals.get(which).id))
                .setPositiveButton(game.getString(R.string.portal_add_short), (dialog, which) -> addPortal())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPortalActionsDialog(int portalId) {
        PortalManagerView portalManager = host.getPortalManagerView();
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
            host.postNotification(game.getString(R.string.portal_gone), 2000);
            return;
        }

        String[] actions = new String[] {
                game.getString(R.string.portal_edit_source),
                game.getString(R.string.portal_edit_target),
                selected.enabled ? game.getString(R.string.portal_action_disable) : game.getString(R.string.portal_action_enable),
                game.getString(R.string.portal_action_duplicate),
                game.getString(R.string.portal_action_delete)
        };

        PortalConfig finalSelected = selected;
        new OverlayAlertDialog.Builder(game)
                .setTitle(finalSelected.name)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            portalManager.setPortalEditingMode(portalId, 1);
                            host.postNotification(game.getString(R.string.portal_editing_source), 2000);
                            break;
                        case 1:
                            portalManager.setPortalEditingMode(portalId, 2);
                            host.postNotification(game.getString(R.string.portal_editing_target), 2000);
                            break;
                        case 2:
                            portalManager.setPortalEnabled(portalId, !finalSelected.enabled);
                            host.postNotification(finalSelected.enabled ? game.getString(R.string.portal_disabled_toast) : game.getString(R.string.portal_enabled_toast), 2000);
                            break;
                        case 3:
                            PortalConfig duplicate = portalManager.duplicatePortal(portalId);
                            if (duplicate != null) {
                                portalManager.setPortalEditingMode(duplicate.id, 2);
                                host.postNotification(game.getString(R.string.portal_duplicated_toast), 2000);
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
        PortalManagerView portalManager = host.getPortalManagerView();
        if (portalManager == null) {
            return;
        }

        new OverlayAlertDialog.Builder(game)
                .setTitle(game.getString(R.string.portal_action_delete))
                .setMessage(game.getString(R.string.portal_confirm_delete, portalName))
                .setPositiveButton(game.getString(R.string.portal_delete), (dialog, which) -> {
                    portalManager.removePortal(portalId);
                    host.postNotification(game.getString(R.string.portal_deleted_toast), 2000);
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
            host.onGameMenuShown();
        }
        
        // 确保菜单面板可见并开始动画
        menuPanel.setVisibility(View.VISIBLE);
        
        // 立即开始动画，避免任何延迟导致的闪烁
        float start = showFromLeft ? -menuPanel.getWidth() : menuPanel.getWidth();
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", start, 0);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();

        if (boxMode) {
            menuBox.setVisibility(View.VISIBLE);
            float boxStart = showFromLeft ? boxWidthPx + dp(12) : -(boxWidthPx + dp(12));
            ObjectAnimator boxAnimator = ObjectAnimator.ofFloat(menuBox, "translationX", boxStart, 0);
            boxAnimator.setDuration(ANIMATION_DURATION);
            boxAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            boxAnimator.start();
        }
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
                    host.onGameMenuHidden();
                }

                if (getFragmentManager() != null) {
                    getFragmentManager().beginTransaction().remove(GameMenuFragment.this).commitAllowingStateLoss();
                }
            }
        });
        animator.start();

        if (boxMode) {
            float boxEnd = showFromLeft ? boxWidthPx + dp(12) : -(boxWidthPx + dp(12));
            ObjectAnimator boxAnimator = ObjectAnimator.ofFloat(menuBox, "translationX", 0, boxEnd);
            boxAnimator.setDuration(ANIMATION_DURATION);
            boxAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            boxAnimator.start();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        GameMenu.setMenuShowing(false);
        if (game != null) {
            host.onGameMenuHidden();
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
        runWithGameFocus(host::toggleKeyboard);
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
        AppExecutors.execute(() -> {
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
        });
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
        AppToast.makeText(game, game.getString(R.string.display_loading_list), AppToast.LENGTH_SHORT).show();
        AppExecutors.execute(() -> {
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
                        displays.add(0, new NvHTTP.DisplayInfo("\\\\.\\DISPLAY1", game.getString(R.string.display_physical_primary_host), cachedGuid));
                    }
                    if (!hasVirtual) {
                        displays.add(new NvHTTP.DisplayInfo("virtual_fallback", game.getString(R.string.display_virtual_forced), ""));
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
                            currentDisplayNameText = !isBlank(currentConfigLabel) ? currentConfigLabel : game.getString(R.string.display_virtual_forced);
                            currentDeviceIdText = "VDD";
                        } else if (currentConfigDisplay == null || currentConfigDisplay.trim().isEmpty()) {
                            currentDisplayNameText = !isBlank(currentConfigLabel) ? currentConfigLabel : game.getString(R.string.display_physical_primary);
                            currentDeviceIdText = (cachedGuid != null && !cachedGuid.trim().isEmpty()) ? cachedGuid : "\\\\.\\DISPLAY1";
                        } else {
                            currentDisplayNameText = !isBlank(currentConfigLabel) ? currentConfigLabel : game.getString(R.string.display_custom);
                            currentDeviceIdText = currentConfigDisplay;
                        }
                    }
                    
                    // 构建精美自定义 View
                    LinearLayout layout = new LinearLayout(game);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(dp(24), dp(16), dp(24), dp(12));
                    
                    TextView tvStatus = new TextView(game);
                    tvStatus.setText(game.getString(R.string.display_current_status, currentDisplayNameText, currentDeviceIdText));
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
                    
                    OverlayAlertDialog.Builder builder = new OverlayAlertDialog.Builder(game);
                    builder.setTitle(game.getString(R.string.display_switch_title));
                    builder.setView(layout);
                    builder.setNegativeButton(android.R.string.cancel, null);
                    
                    final OverlayAlertDialog dialog = builder.create();
                    
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
                            AppToast.makeText(game, game.getString(R.string.display_switching_to, toastText), AppToast.LENGTH_SHORT).show();
                            host.recreateConnectionWithDisplay(targetValue, isVirtual);
                            dialog.dismiss();
                        });
                        
                        layout.addView(item);
                        addedItems++;
                    }
                    
                    if (addedItems == 0) {
                        TextView itemEmpty = new TextView(game);
                        itemEmpty.setText(game.getString(R.string.display_none_other));
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
        });
    }

    private void showFallbackSwitchDisplayDialog() {
        if (!canShowSwitchDisplayUi()) {
            return;
        }

        OverlayAlertDialog.Builder builder = new OverlayAlertDialog.Builder(game);
        builder.setTitle(game.getString(R.string.display_switch_fallback_title));
        
        final String[] items = new String[]{game.getString(R.string.display_physical_primary_with_id), game.getString(R.string.display_virtual_forced), game.getString(R.string.display_enter_manually)};
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                String cachedGuid = android.preference.PreferenceManager.getDefaultSharedPreferences(game)
                        .getString("cached_physical_display_guid", "");
                String targetDisplay = (cachedGuid != null && !cachedGuid.trim().isEmpty()) ? cachedGuid : "\\\\.\\DISPLAY1";
                rememberDisplayLabel(game.getString(R.string.display_physical_primary));
                AppToast.makeText(game, game.getString(R.string.display_switching_to, targetDisplay), AppToast.LENGTH_SHORT).show();
                host.recreateConnectionWithDisplay(targetDisplay, false);
            } else if (which == 1) {
                rememberDisplayLabel(game.getString(R.string.display_virtual_forced));
                AppToast.makeText(game, game.getString(R.string.display_activating_virtual), AppToast.LENGTH_SHORT).show();
                host.recreateConnectionWithDisplay("", true);
            } else {
                OverlayAlertDialog.Builder inputBuilder = new OverlayAlertDialog.Builder(game);
                inputBuilder.setTitle(game.getString(R.string.display_enter_name_title));
                final android.widget.EditText input = new android.widget.EditText(game);
                input.setHint(game.getString(R.string.display_name_hint));
                inputBuilder.setView(input);
                inputBuilder.setPositiveButton(android.R.string.ok, (dialog1, which1) -> {
                    String customDisplay = input.getText().toString().trim();
                    if (!customDisplay.isEmpty()) {
                        boolean isVirtual = customDisplay.toLowerCase(java.util.Locale.ROOT).contains("zako")
                                || customDisplay.toLowerCase(java.util.Locale.ROOT).contains("virtual");
                        rememberDisplayLabel(customDisplay);
                        AppToast.makeText(game, game.getString(R.string.display_switching_to, customDisplay), AppToast.LENGTH_SHORT).show();
                        host.recreateConnectionWithDisplay(customDisplay, isVirtual);
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
            return game.getString(R.string.display_unknown);
        }

        if ("virtual_fallback".equals(info.displayName)) {
            return game.getString(R.string.display_virtual_forced);
        }

        String friendlyName = normalizeDisplayName(info.friendlyName);
        String displayName = normalizeDisplayName(info.displayName);
        if (!friendlyName.isEmpty()) {
            return friendlyName;
        }

        if (isVirtualDisplayInfo(info)) {
            return game.getString(R.string.display_virtual);
        }

        return displayName.isEmpty() ? game.getString(R.string.display_physical) : displayName;
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
