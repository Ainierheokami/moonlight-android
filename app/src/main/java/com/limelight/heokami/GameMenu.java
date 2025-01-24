package com.limelight.heokami;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.LinkedHashMap;
import java.util.Map;

public class GameMenu {

    private final Game game;
    private final NvConnection conn;

    public GameMenu(Game game, NvConnection conn) {
        this.game = game;
        this.conn = conn;

        showMenu();
    }

    // This Code Author by https://github.com/moonlight-stream/moonlight-android/pull/1171/files
    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), 10);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }


    private void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= (byte) ~ VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), 25);
    }


    // 创建一个ListView并设置适配器
    private ListView createListView(AlertDialog dialog) {
        ListView listView = new ListView(game);
//        String[] items = {"启用输入法", "切换屏幕虚拟手柄", "热键：屏幕键盘", "热键：Ctrl+C", "热键：Ctrl+V", "热键：返回桌面"};
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(game, android.R.layout.simple_list_item_1, items);
//        listView.setAdapter(adapter);


        // 创建方法映射
        Map<String, Runnable> actionMap = createActionMap();
        String[] items = actionMap.keySet().toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(game, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);

        // 设置点击事件监听器
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String item = adapter.getItem(position);
//            Toast.makeText(game, "点击了: " + item, Toast.LENGTH_SHORT).show();

//            // 根据名称执行方法
//            if (actionMap.containsKey(item)) {
//                Objects.requireNonNull(actionMap.get(item)).run(); // 动态调用对应的方法
//            }
            // 根据名称执行方法
            dialog.dismiss();
            if (actionMap.containsKey(item)) {
                Runnable action = actionMap.get(item);
                if (action != null) {
                    action.run();  // 动态调用对应的方法
                } else {
                    // 如果 action 为 null，输出日志调试信息
                    Toast.makeText(game, "action 为 null", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 如果没有找到匹配项，输出日志
                Toast.makeText(game, "action 未匹配", Toast.LENGTH_SHORT).show();
            }
        });


        return listView;
    }

    private void showMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(game);
        AlertDialog dialog = builder.setTitle(game.getResources().getString(R.string.game_menu_title))
                .setNeutralButton(game.getResources().getString(R.string.game_menu_disconnect), (dialogInterface, which) -> {
                    dialogInterface.dismiss();
                    // 中断串流
                    game.finish();
                })
                .setNegativeButton(game.getResources().getString(R.string.game_menu_change_touch), (dialogInterface, which) -> {
                    // 2024-11-27 23:48:26 添加触摸模式切换
                    game.toggleTouchscreenMode();
                })
                .create();
        dialog.setView(createListView(dialog));
        dialog.show();
    }

    private void enableKeyboard() {
        runWithGameFocus(game::toggleKeyboard);
    }

    public static CharSequence getClipboardContent(Game context, final int[] retryCount, final long[] retryDelay) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();

        if (clipData != null && clipData.getItemCount() > 0) {
            ClipData.Item item = clipData.getItemAt(0);
            return item.getText();
        } else if (retryCount[0] > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    retryCount[0]--;
                    retryDelay[0] *= 2;
                    getClipboardContent(context, retryCount, retryDelay);
                }
            }, retryDelay[0]);
        }

        return null;
    }

    public static String getClipboardContentAsString(Game context, final int[] retryCount, final long[] retryDelay) {
        CharSequence charSequence = getClipboardContent(context, retryCount, retryDelay);
        if (charSequence != null) {
            return charSequence.toString(); // 转换为 String
        } else {
            return ""; // 或者返回空字符串 ""
        }
    }

    // 创建方法映射
    private Map<String, Runnable> createActionMap() {
        Map<String, Runnable> actionMap = new LinkedHashMap<>();

        // 映射菜单项名称到方法
        actionMap.put(game.getString(R.string.game_menu_enable_keyboard), this::enableKeyboard);
        actionMap.put(game.getString(R.string.game_menu_toggle_virtual_controller), game::toggleVirtualController);
        actionMap.put(game.getString(R.string.game_menu_toggle_virtual_keyboard), () -> {
            game.toggleVirtualKeyboard();
            Toast.makeText(game, game.getString(R.string.game_menu_toggle_virtual_keyboard_toast), Toast.LENGTH_SHORT).show();
        });
        actionMap.put(game.getString(R.string.game_menu_hotkey_screen_keyboard), () -> sendKeys(new short[]{(short) VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short) VirtualKeyboardVkCode.VKCode.VK_O.getCode()}));
        actionMap.put(game.getString(R.string.game_menu_hotkey_ctrl_c), () -> sendKeys(new short[]{(short)VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short)VirtualKeyboardVkCode.VKCode.VK_C.getCode()}));
        actionMap.put(game.getString(R.string.game_menu_hotkey_ctrl_v), () -> sendKeys(new short[]{(short)VirtualKeyboardVkCode.VKCode.VK_LCONTROL.getCode(), (short)VirtualKeyboardVkCode.VKCode.VK_V.getCode()}));
        actionMap.put(game.getString(R.string.game_menu_send_clipboard_content), () ->
                conn.sendUtf8Text(getClipboardContentAsString(game, new int[]{3}, new long[]{30}))
        );
        actionMap.put(game.getString(R.string.game_menu_hotkey_alt_tab), () -> sendKeys(new short[]{(short)VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short)VirtualKeyboardVkCode.VKCode.VK_TAB.getCode()}));
        actionMap.put(game.getString(R.string.game_menu_hotkey_home), () -> sendKeys(new short[]{(short)VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(), (short)VirtualKeyboardVkCode.VKCode.VK_D.getCode()}));

        return actionMap;
    }
}
