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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import android.app.Fragment;

import android.widget.Button;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.heokami.FloatingVirtualKeyboardFragment;

/**
 * 游戏菜单Fragment
 * 提供侧边滑出式菜单，包含各种游戏控制功能
 */
public class GameMenuFragment extends Fragment {

    private Game game;
    private NvConnection conn;
    private View menuPanel;
    private View backgroundView;
    private boolean isMenuVisible = false;

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
        
        // 计算菜单宽度：直接使用当前屏幕宽度，不考虑方向
        // 因为getDisplayMetrics().widthPixels已经考虑了屏幕方向
        // 将400dp转换为px
        int maxWidthPx = (int) (400 * getResources().getDisplayMetrics().density);
        int menuWidth = Math.min(screenWidth / 3, maxWidthPx);
        
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
        // 背景点击关闭菜单
        backgroundView.setOnClickListener(v -> hideMenuWithAnimation());

        // 关闭按钮
        View closeButton = getView().findViewById(R.id.btn_close_menu);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> hideMenuWithAnimation());
        }

        // 输入控制按钮
        setupInputControlButtons();
        
        // 热键按钮
        setupHotkeyButtons();
        
        // 剪贴板按钮
        setupClipboardButtons();
        
        // 底部操作按钮
        setupBottomButtons();
    }

    /**
     * 设置输入控制按钮
     */
    private void setupInputControlButtons() {
        // 启用输入法
        Button btnEnableKeyboard = getView().findViewById(R.id.btn_enable_keyboard);
        if (btnEnableKeyboard != null) {
            btnEnableKeyboard.setOnClickListener(v -> {
                hideMenuWithAnimation();
                enableKeyboard();
            });
        }

        // 切换虚拟手柄
        Button btnToggleVirtualController = getView().findViewById(R.id.btn_toggle_virtual_controller);
        if (btnToggleVirtualController != null) {
            btnToggleVirtualController.setOnClickListener(v -> {
                hideMenuWithAnimation();
                game.toggleVirtualController();
            });
        }

        // 切换虚拟键盘
        Button btnToggleVirtualKeyboard = getView().findViewById(R.id.btn_toggle_virtual_keyboard);
        if (btnToggleVirtualKeyboard != null) {
            btnToggleVirtualKeyboard.setOnClickListener(v -> {
                hideMenuWithAnimation();
                game.toggleVirtualKeyboard();
                Toast.makeText(game, game.getString(R.string.game_menu_toggle_virtual_keyboard_toast), Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 设置热键按钮
     */
    private void setupHotkeyButtons() {
        // 复制热键
        Button btnHotkeyCopy = getView().findViewById(R.id.btn_hotkey_copy);
        if (btnHotkeyCopy != null) {
            btnHotkeyCopy.setOnClickListener(v -> {
                hideMenuWithAnimation();
                sendKeys(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_C.getCode()});
            });
        }

        // 粘贴热键
        Button btnHotkeyPaste = getView().findViewById(R.id.btn_hotkey_paste);
        if (btnHotkeyPaste != null) {
            btnHotkeyPaste.setOnClickListener(v -> {
                hideMenuWithAnimation();
                sendKeys(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_V.getCode()});
            });
        }

        // 虚拟键盘热键
        Button btnHotkeyScreenKeyboard = getView().findViewById(R.id.btn_hotkey_screen_keyboard);
        if (btnHotkeyScreenKeyboard != null) {
            btnHotkeyScreenKeyboard.setOnClickListener(v -> {
                hideMenuWithAnimation();
                sendKeys(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_O.getCode()});
            });
        }

        // 切换窗口热键
        Button btnHotkeyAltTab = getView().findViewById(R.id.btn_hotkey_alt_tab);
        if (btnHotkeyAltTab != null) {
            btnHotkeyAltTab.setOnClickListener(v -> {
                hideMenuWithAnimation();
                sendKeys(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_TAB.getCode()});
            });
        }

        // 返回桌面热键
        Button btnHotkeyHome = getView().findViewById(R.id.btn_hotkey_home);
        if (btnHotkeyHome != null) {
            btnHotkeyHome.setOnClickListener(v -> {
                hideMenuWithAnimation();
                sendKeys(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_D.getCode()});
            });
        }
    }

    /**
     * 设置剪贴板按钮
     */
    private void setupClipboardButtons() {
        // 发送剪贴板内容
        Button btnSendClipboard = getView().findViewById(R.id.btn_send_clipboard);
        if (btnSendClipboard != null) {
            btnSendClipboard.setOnClickListener(v -> {
                hideMenuWithAnimation();
                conn.sendUtf8Text(getClipboardContentAsString(game, new int[]{3}, new long[]{30}));
            });
        }

        // 悬浮键盘
        Button btnFloatingKeyboard = getView().findViewById(R.id.btn_floating_keyboard);
        if (btnFloatingKeyboard != null) {
            btnFloatingKeyboard.setOnClickListener(v -> {
                hideMenuWithAnimation();
                FloatingVirtualKeyboardFragment.Companion.show(game);
            });
        }

        // 虚拟全键盘
        Button btnVirtualFullKeyboard = getView().findViewById(R.id.btn_virtual_full_keyboard);
        if (btnVirtualFullKeyboard != null) {
            btnVirtualFullKeyboard.setOnClickListener(v -> {
                hideMenuWithAnimation();
                VirtualKeyboardDialogFragment fragment = new VirtualKeyboardDialogFragment();
                fragment.show(game.getFragmentManager(), "VirtualKeyboard");
            });
        }
    }

    /**
     * 设置底部操作按钮
     */
    private void setupBottomButtons() {
        // 切换触屏模式
        Button btnChangeTouch = getView().findViewById(R.id.btn_change_touch);
        if (btnChangeTouch != null) {
            btnChangeTouch.setOnClickListener(v -> {
                hideMenuWithAnimation();
                game.toggleTouchscreenMode();
            });
        }

        // 断开连接
        Button btnDisconnect = getView().findViewById(R.id.btn_disconnect);
        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(v -> {
                hideMenuWithAnimation();
                game.finish();
            });
        }
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
        
        if (!isMenuVisible) return;
        
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
                    getFragmentManager().beginTransaction().remove(GameMenuFragment.this).commit();
                }
            }
        });
        animator.start();
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