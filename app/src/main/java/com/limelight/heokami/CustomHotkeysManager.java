package com.limelight.heokami;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义热键管理器
 * - 负责自定义热键的持久化存储（SharedPreferences）
 * - 负责管理对话框（新增/编辑名称/编辑宏/删除）
 * - 负责执行宏（复用虚拟键盘的 MacroEditor 逻辑）
 *
 * 存储结构（SharedPreferences -> JSON）：
 * [
 *   {
 *     "id": 1,
 *     "name": "我的热键",
 *     "actions": [ {"type":"KEY_DOWN","data":65}, {"type":"KEY_UP","data":65} ]
 *   }
 * ]
 */
public class CustomHotkeysManager {

    public static class CustomHotkey {
        public int id;
        public String name;
        public List<MacroAction> actions = new ArrayList<>();
    }

    /** 首选项名称与键名 */
    private static final String PREFS_NAME = "game_menu_prefs";
    private static final String KEY_CUSTOM_HOTKEYS = "custom_hotkeys_json";

    private static final Gson gson = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<CustomHotkey>>(){}.getType();

    /**
     * 读取全部自定义热键
     */
    public static List<CustomHotkey> load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_CUSTOM_HOTKEYS, "");
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<CustomHotkey> list = gson.fromJson(json, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 保存全部自定义热键
     */
    public static void save(Context context, List<CustomHotkey> items) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_CUSTOM_HOTKEYS, gson.toJson(items)).apply();
    }

    /**
     * 执行指定的自定义热键（复用 MacroEditor 的宏执行）
     */
    public static void runCustomHotkey(Game game, VirtualKeyboard vk, CustomHotkey hotkey) {
        try {
            JSONObject json = buildMacroContainerFromActions(hotkey.actions);
            MacroEditor editor = new MacroEditor(game, json, null);
            editor.runMacroAction(vk);
        } catch (Exception e) {
            Toast.makeText(game, "执行自定义热键失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 打开“编辑热键”管理对话框
     * - 列表展示已有自定义热键
     * - 支持新增（命名 + 宏编辑）、重命名、删除、编辑宏
     * - onChanged：当数据保存时回调（用于刷新UI）
     */
    public static void showManageDialog(Game game, VirtualKeyboard vk, Runnable onChanged) {
        List<CustomHotkey> items = new ArrayList<>(load(game));
        CharSequence[] names;
        if (items.isEmpty()) {
            names = new CharSequence[]{ game.getString(R.string.custom_hotkeys_empty) };
        } else {
            names = new CharSequence[items.size()];
            for (int i = 0; i < items.size(); i++) {
                names[i] = items.get(i).name;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(game);
        builder.setTitle(game.getString(R.string.custom_hotkeys_manage_title));

        builder.setItems(names, (dialog, which) -> {
            if (items.isEmpty()) return; // 空列表时不响应
            CustomHotkey target = items.get(which);
            showItemActionsDialog(game, vk, items, target, onChanged);
        });

        builder.setPositiveButton(game.getString(R.string.custom_hotkeys_add), (d, w) -> {
            promptForName(game, null, name -> {
                CustomHotkey newItem = new CustomHotkey();
                newItem.id = generateNextId(items);
                newItem.name = name;
                items.add(newItem);
                save(game, items);
                // 进入宏编辑器
                editHotkeyMacro(game, vk, newItem, () -> {
                    save(game, items);
                    if (onChanged != null) onChanged.run();
                    Toast.makeText(game, R.string.custom_hotkeys_save_success, Toast.LENGTH_SHORT).show();
                });
            });
        });

        builder.setNegativeButton(game.getString(R.string.cancel_button), null);
        builder.show();
    }

    private static void showItemActionsDialog(Game game, VirtualKeyboard vk, List<CustomHotkey> items, CustomHotkey target, Runnable onChanged) {
        CharSequence[] options = new CharSequence[] {
                game.getString(R.string.virtual_keyboard_menu_macro_edit_button),
                game.getString(R.string.custom_hotkeys_rename),
                game.getString(R.string.custom_hotkeys_delete)
        };
        new AlertDialog.Builder(game)
                .setTitle(target.name)
                .setItems(options, (d, which) -> {
                    if (which == 0) { // 编辑宏
                        editHotkeyMacro(game, vk, target, () -> {
                            save(game, items);
                            if (onChanged != null) onChanged.run();
                            Toast.makeText(game, R.string.custom_hotkeys_save_success, Toast.LENGTH_SHORT).show();
                        });
                    } else if (which == 1) { // 重命名
                        promptForName(game, target.name, name -> {
                            target.name = name;
                            save(game, items);
                            if (onChanged != null) onChanged.run();
                        });
                    } else if (which == 2) { // 删除
                        new AlertDialog.Builder(game)
                                .setMessage(R.string.custom_hotkeys_confirm_delete)
                                .setPositiveButton(R.string.confirm_button, (dd, ww) -> {
                                    items.remove(target);
                                    save(game, items);
                                    if (onChanged != null) onChanged.run();
                                })
                                .setNegativeButton(R.string.cancel_button, null)
                                .show();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    /**
     * 打开 MacroEditor 以编辑某个热键的宏
     * - 使用 OnMacroDataChangedListener 接收保存后的数据并落盘
     */
    private static void editHotkeyMacro(Game game, VirtualKeyboard vk, CustomHotkey hotkey, Runnable onSaved) {
        try {
            JSONObject json = buildMacroContainerFromActions(hotkey.actions);
            MacroEditor editor = new MacroEditor(game, json, new OnMacroDataChangedListener() {
                @Override
                public void onMacroDataChanged(JSONObject newData) {
                    // 从 MacroEditor 返回的数据中解析 MACROS
                    List<MacroAction> updated = parseActionsFromMacroContainer(newData);
                    hotkey.actions.clear();
                    hotkey.actions.addAll(updated);
                    if (onSaved != null) onSaved.run();
                }
            });
            // 补充虚拟键盘元素（用于 KEY_TOGGLE / GROUP 等宏类型的选择）
            if (vk != null) {
                editor.setElements(vk.getElements());
            }
            editor.showMacroEditor();
        } catch (Exception e) {
            Toast.makeText(game, "打开宏编辑器失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 构造 MacroEditor 期望的 JSON：{"MACROS": {"0": {type,data}, ...}}
     */
    private static JSONObject buildMacroContainerFromActions(List<MacroAction> actions) {
        try {
            JSONObject container = new JSONObject();
            JSONObject macros = new JSONObject();
            for (int i = 0; i < actions.size(); i++) {
                MacroAction a = actions.get(i);
                JSONObject one = new JSONObject();
                one.put("type", a.getType());
                one.put("data", a.getData());
                macros.put(String.valueOf(i), one);
            }
            container.put("MACROS", macros);
            return container;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * 从 MacroEditor 保存回调的 JSON 中解析动作列表
     */
    private static List<MacroAction> parseActionsFromMacroContainer(JSONObject data) {
        try {
            List<MacroAction> result = new ArrayList<>();
            JSONObject macros = data.optJSONObject("MACROS");
            if (macros == null) macros = data; // 兼容旧格式
            // 由于 key 为字符串数字，收集后按 key 排序
            List<String> keys = new ArrayList<>();
            for (java.util.Iterator<String> it = macros.keys(); it.hasNext();) {
                String k = it.next();
                keys.add(k);
            }
            Collections.sort(keys, (a, b) -> Integer.compare(parseIntSafely(a), parseIntSafely(b)));
            for (String k : keys) {
                JSONObject obj = macros.optJSONObject(k);
                if (obj == null) continue;
                String type = obj.optString("type", "");
                int value = obj.optInt("data", 0);
                if (!type.isEmpty()) {
                    result.add(new MacroAction(type, value));
                }
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static int parseIntSafely(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    /** 生成下一个自增 ID */
    private static int generateNextId(List<CustomHotkey> items) {
        int max = 0;
        for (CustomHotkey i : items) {
            if (i.id > max) max = i.id;
        }
        return max + 1;
    }

    /** 简易输入对话框：输入/修改热键名称 */
    private static void promptForName(Context context, String defaultName, NameCallback callback) {
        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.custom_hotkeys_name_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        if (defaultName != null) input.setText(defaultName);
        int pad = (int) (context.getResources().getDisplayMetrics().density * 16);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(pad, pad, pad, pad);
        wrapper.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(R.string.custom_hotkeys_name_hint)
                .setView(wrapper)
                .setPositiveButton(R.string.confirm_button, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = context.getString(R.string.default_button);
                    callback.onName(name);
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    interface NameCallback { void onName(String name); }
}

