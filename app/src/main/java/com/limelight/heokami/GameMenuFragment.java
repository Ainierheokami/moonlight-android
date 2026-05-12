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
import android.widget.TextView;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
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
    private long lastDisconnectTapMs = 0;

    // 动画持续时间
    private static final int ANIMATION_DURATION = 300;

    /**
     * 创建新的GameMenuFragment实例
     * @param game 游戏Activity实例
     * @param conn 网络连接实例
     * @return GameMenuFragment实例
     */
    public static GameMenuFragment newInstance(Game game, NvConnection conn) {
        GameMenuFragment fragment = new GameMenuFragment();
        fragment.game = game;
        fragment.conn = conn;
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
        
        // 设置初始位置在屏幕右侧外
        menuPanel.setTranslationX(menuWidth);
        
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

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void renderStatusBar() {
        if (statusContainer == null) return;
        statusContainer.removeAllViews();
        addStatusChip(getString(R.string.game_menu_change_touch), getTouchModeName());
        addStatusChip(getString(R.string.game_menu_section_virtual_keyboard), game.getVirtualKeyboard() != null ? getString(R.string.game_menu_status_ready) : getString(R.string.game_menu_status_unavailable));
        addStatusChip(getString(R.string.game_menu_section_screen_overlay), getString(R.string.game_menu_status_quick));
        addStatusChip(getString(R.string.game_menu_section_portals), game.arePortalsEnabled() ? getString(R.string.game_menu_status_on) : getString(R.string.game_menu_status_off));
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

    private List<MenuAction> buildMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
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
        actions.add(new MenuAction("portal_manage", R.string.game_menu_portal_manage, 0, MenuSection.PORTALS, 40, false, true, true, v -> Toast.makeText(game, R.string.game_menu_portal_manage_pending, Toast.LENGTH_SHORT).show()));

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

        GridLayout grid = new GridLayout(game);
        grid.setColumnCount(2);
        dashboardContainer.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (MenuAction action : actions) {
            grid.addView(createActionButton(action));
        }
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
            btnDisconnect.setOnClickListener(v -> confirmDisconnect());
            btnDisconnect.setOnLongClickListener(v -> {
                hideMenuWithAnimation();
                game.finish();
                return true;
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
        config.dstRect = new RectF(100, 100, 300, 300);
        config.enabled = true;
        config.name = "传送门" + config.id;
        portalManager.addPortal(config);
        portalManager.setEditingMode(1);
        game.postNotification("已添加传送门，请调整源区域", 2000);
    }

    private void togglePortalEditMode() {
        hideMenuWithAnimation();
        PortalManagerView portalManager = game.getPortalManagerView();
        if (portalManager == null) return;
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

    /**
     * 显示菜单动画
     */
    private void showMenuWithAnimation() {
        if (isMenuVisible) return;
        
        isMenuVisible = true;
        
        // 确保菜单面板可见并开始动画
        menuPanel.setVisibility(View.VISIBLE);
        
        // 立即开始动画，避免任何延迟导致的闪烁
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", menuPanel.getWidth(), 0);
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
        ObjectAnimator animator = ObjectAnimator.ofFloat(menuPanel, "translationX", 0, menuPanel.getWidth());
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                android.util.Log.d("GameMenu", "Animation ended, removing fragment");
                // 更新菜单显示状态
                GameMenu.setMenuShowing(false);
                
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
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
            modifier[0] |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);
        }

        new Handler().postDelayed(() -> {
            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];
                modifier[0] &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }, 25);
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
} 
