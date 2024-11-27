package com.limelight.heokami;

import android.app.AlertDialog;
import android.os.Handler;
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

    // virtual-key-codes https://learn.microsoft.com/zh-cn/windows/win32/inputdev/virtual-key-codes
    private static final int VK_LWIN = 91;
    private static final int VK_RWIN = 92;
    private static final int VK_LSHIFT = 160;
    private static final int VK_RSHIFT = 161;
    private static final int VK_LCONTROL = 162;
    private static final int VK_RCONTROL = 163;
    private static final int VK_LALT = 164;
    private static final int VK_RALT = 165;
    private static final int VK_C = 67;
    private static final int VK_D = 68;
    private static final int VK_V = 86;
    private static final int VK_O = 79;
//    private static final int VK_ESCAPE = 27;

    private static byte replaceSpecialKeys(short vk_code) {
        int modifierMask = 0;
        if (vk_code == VK_LCONTROL || vk_code == VK_RCONTROL) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL;
        }
        else if (vk_code == VK_LSHIFT || vk_code == VK_RSHIFT) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT;
        }
        else if (vk_code == VK_LALT || vk_code == VK_RALT) {
            modifierMask = KeyboardPacket.MODIFIER_ALT;
        }
        else if (vk_code == VK_LWIN || vk_code == VK_RWIN) {
            modifierMask = KeyboardPacket.MODIFIER_META;
        }
        return (byte) modifierMask;
    }

    private void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= replaceSpecialKeys(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= (byte) ~replaceSpecialKeys(key);

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

//        // 设置点击事件监听器
//        listView.setOnItemClickListener((parent, view, position, id) -> {
//            String item = adapter.getItem(position);
//            Toast.makeText(game, "点击了: " + item, Toast.LENGTH_SHORT).show();
//            switch (position) {
//                case 0:
//                    // 启用输入法
//                    dialog.dismiss();
//                    runWithGameFocus(game::toggleKeyboard);
//                    break;
//                case 1:
//                    // 切换屏幕虚拟手柄
//                    if (virtualController == null) {
//                        streamView = game.findViewById(R.id.surfaceView);
//                        virtualController = new VirtualController(controllerHandler,
//                                (FrameLayout) streamView.getParent(),
//                                game);
//                        virtualController.refreshLayout();
//                    }
//
//                    if (!virtualControllerShow) {
//                        virtualController.show();
//                        virtualControllerShow = true;
//                    }else{
//                        virtualController.hide();
//                        virtualControllerShow = false;
//                    }
//                    break;
//
//                case 2:
//                    // 切换屏幕虚拟键盘
//                    dialog.dismiss();
//                    sendKeys(new short[]{VK_LWIN,VK_LCONTROL,VK_O});
//                    break;
//                case 3:
//                    // 热键：Ctrl+C
//                    dialog.dismiss();
//                    sendKeys(new short[]{VK_LCONTROL,VK_C});
//                    break;
//                case 4:
//                    // 热键：Ctrl+V
//                    dialog.dismiss();
//                    sendKeys(new short[]{VK_LCONTROL,VK_V});
//                    break;
//                case 5:
//                    // 热键：返回桌面
//                    dialog.dismiss();
//                    sendKeys(new short[]{VK_LWIN, VK_D});
//                    break;
//            }
//        });

        return listView;
    }

    private void showMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(game);
        AlertDialog dialog = builder.setTitle("菜单")
                .setNeutralButton(game.getResources().getString(R.string.game_menu_disconnect), (dialogInterface, which) -> {
                    dialogInterface.dismiss();
                    game.finish();
                })
                .setNegativeButton(game.getResources().getString(R.string.game_menu_change_touch), (dialogInterface, which) -> Toast.makeText(game, "暂未实现", Toast.LENGTH_SHORT).show())
                .create();
        dialog.setView(createListView(dialog));
        dialog.show();
    }

    private void enableKeyboard() {
        runWithGameFocus(game::toggleKeyboard);
    }

    // 创建方法映射
    private Map<String, Runnable> createActionMap() {
        Map<String, Runnable> actionMap = new LinkedHashMap<>();

        // 映射菜单项名称到方法
        actionMap.put(game.getString(R.string.game_menu_enable_keyboard), this::enableKeyboard);
        actionMap.put(game.getString(R.string.game_menu_toggle_virtual_controller), game::toggleVirtualController);
        actionMap.put(game.getString(R.string.game_menu_hotkey_screen_keyboard), () -> sendKeys(new short[]{VK_LCONTROL, VK_LWIN, VK_O}));
        actionMap.put(game.getString(R.string.game_menu_hotkey_ctrl_c), () -> sendKeys(new short[]{VK_LCONTROL, VK_C}));
        actionMap.put(game.getString(R.string.game_menu_hotkey_ctrl_v), () -> sendKeys(new short[]{VK_LCONTROL, VK_V}));
        actionMap.put(game.getString(R.string.game_menu_hotkey_home), () -> sendKeys(new short[]{VK_LWIN, VK_D}));

        return actionMap;
    }
}
