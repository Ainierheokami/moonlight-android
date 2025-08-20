package com.limelight.heokami;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义热键管理器
 * - 负责自定义热键的持久化存储 (SharedPreferences)
 * - 负责管理对话框 (新增/编辑名称/编辑宏/删除)
 * - 负责执行宏 (复用 MacroEditor 的逻辑)
 *
 * 存储结构 (SharedPreferences -> 多个JSON条目):
 * 键: "1" -> 值: "{ "id": 1, "name": "我的热键", "actions": [...] }"
 * 键: "2" -> 值: "{ "id": 2, "name": "另一个热键", "actions": [...] }"
 */
public class CustomHotkeysManager {

    private static final String TAG = "CustomHotkeysManager";

    public static class CustomHotkey {
        public int id;
        public String name;
        public List<MacroAction> actions = new ArrayList<>();
    }

    /** SharedPreferences 文件名 */
    private static final String PREFS_NAME = "game_menu_prefs";

    private static final Gson gson = new Gson();

    /**
     * 从 SharedPreferences 读取全部自定义热键。
     * 它会遍历所有条目，将以数字为键的条目解析为 CustomHotkey 对象。
     */
    public static List<CustomHotkey> load(Context context) {
        Log.d(TAG, "正在加载自定义热键...");
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<CustomHotkey> items = new ArrayList<>();

        // --- 从旧格式迁移数据的逻辑 ---
        if (sp.contains("custom_hotkeys_json")) {
            Log.d(TAG, "发现旧格式的热键数据，开始迁移...");
            String json = sp.getString("custom_hotkeys_json", "");
            if (json != null && !json.trim().isEmpty()) {
                try {
                    Type listType = new TypeToken<ArrayList<CustomHotkey>>(){}.getType();
                    List<CustomHotkey> oldItems = gson.fromJson(json, listType);
                    if (oldItems != null) {
                        SharedPreferences.Editor editor = sp.edit();
                        // 将每个旧热键按新格式写入
                        for (CustomHotkey item : oldItems) {
                            String itemJson = gson.toJson(item);
                            editor.putString(String.valueOf(item.id), itemJson);
                        }
                        // 移除旧的键并提交
                        editor.remove("custom_hotkeys_json");
                        editor.apply();
                        Log.d(TAG, "旧数据迁移到新格式成功。");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "迁移旧格式热键失败", e);
                    // 如果迁移失败，移除可能已损坏的旧数据，避免重复尝试
                    sp.edit().remove("custom_hotkeys_json").apply();
                }
            } else {
                // 如果旧键的值为空，直接移除
                sp.edit().remove("custom_hotkeys_json").apply();
            }
        }

        // --- 按新格式正常加载 ---
        Map<String, ?> allEntries = sp.getAll();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            try {
                // 按照约定，热键的键是其ID的字符串形式（即数字）
                Integer.parseInt(entry.getKey());
                String json = (String) entry.getValue();
                if (json != null && !json.trim().isEmpty()) {
                    CustomHotkey hotkey = gson.fromJson(json, CustomHotkey.class);
                    if (hotkey != null) {
                        // [修复] 解决 ClassCastException 的关键步骤：
                        // Gson 反序列化时，由于Java泛型擦除，actions 列表中的对象会变成 LinkedTreeMap。
                        // 这里需要手动将其重新转换为正确的 MacroAction 对象列表。
                        List<MacroAction> typedActions = new ArrayList<>();
                        if (hotkey.actions != null) {
                            for (Object actionObj : hotkey.actions) {
                                String actionJson = gson.toJson(actionObj); // 将 Map 对象转回 JSON 字符串
                                MacroAction macroAction = gson.fromJson(actionJson, MacroAction.class); // 从 JSON 字符串转为目标对象
                                typedActions.add(macroAction);
                            }
                        }
                        hotkey.actions = typedActions; // 替换为类型正确的列表

                        items.add(hotkey);
                        Log.d(TAG, "成功加载热键 id: " + hotkey.id + ", 名称: " + hotkey.name);
                    }
                }
            } catch (NumberFormatException e) {
                // 键不是数字，说明不是热键条目，忽略它。
                Log.v(TAG, "在首选项中跳过非数字键: " + entry.getKey());
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "解析热键JSON失败，键: " + entry.getKey(), e);
            } catch (ClassCastException e) {
                Log.e(TAG, "值的类型不是字符串，键: " + entry.getKey(), e);
            }
        }

        // 按 ID 排序以确保显示顺序一致
        Collections.sort(items, (a, b) -> Integer.compare(a.id, b.id));
        Log.d(TAG, "完成加载 " + items.size() + " 个热键。");
        return items;
    }

    /**
     * 保存全部自定义热键列表。
     * 此方法会先清除所有已保存的热键，然后写入新的列表，
     * 以确保不会留下孤立的旧数据。
     */
    public static void save(Context context, List<CustomHotkey> items) {
        Log.d(TAG, "正在保存 " + items.size() + " 个自定义热键...");
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        // 获取当前所有数字键的集合，准备移除
        Set<String> keysToRemove = new HashSet<>();
        for (String key : sp.getAll().keySet()) {
            try {
                Integer.parseInt(key);
                keysToRemove.add(key);
            } catch (NumberFormatException e) {
                // 不是数字键，我们不碰它
            }
        }

        // 移除所有旧的热键条目
        for (String key : keysToRemove) {
            editor.remove(key);
        }
        Log.d(TAG, "移除了 " + keysToRemove.size() + " 个旧的热键条目。");

        // 添加新的热键条目
        for (CustomHotkey item : items) {
            String json = gson.toJson(item);
            editor.putString(String.valueOf(item.id), json);
            Log.d(TAG, "正在保存热键 id: " + item.id + ", 名称: " + item.name);
        }

        editor.apply();
        Log.d(TAG, "保存操作完成。");
    }

    /**
     * 执行指定的自定义热键 (复用 MacroEditor 的宏执行逻辑)
     */
    public static void runCustomHotkey(Game game, VirtualKeyboard vk, CustomHotkey hotkey) {
        try {
            JSONObject json = buildMacroContainerFromActions(hotkey.actions);
            MacroEditor editor = new MacroEditor(game, json, null);
            editor.runMacroAction(vk);
        } catch (Exception e) {
            Toast.makeText(game, "执行自定义热键失败", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "执行自定义热键失败: " + hotkey.name, e);
        }
    }

    /**
     * 打开“编辑热键”管理对话框
     * - 列表展示已有自定义热键
     * - 支持新增 (命名 + 宏编辑)、重命名、删除、编辑宏
     * - onChanged：当数据保存时回调 (用于刷新UI)
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
            if (items.isEmpty()) return; // 如果列表为空则不响应点击
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
                // 为新条目进入宏编辑器
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
            MacroEditor editor = new MacroEditor(game, json, newData -> {
                // 从 MacroEditor 返回的数据中解析动作列表
                List<MacroAction> updated = parseActionsFromMacroContainer(newData);
                hotkey.actions.clear();
                hotkey.actions.addAll(updated);
                if (onSaved != null) onSaved.run();
            });
            // 补充虚拟键盘元素 (用于 KEY_TOGGLE / GROUP 等宏类型的选择)
            if (vk != null) {
                editor.setElements(vk.getElements());
            }
            editor.showMacroEditor();
        } catch (Exception e) {
            Toast.makeText(game, "打开宏编辑器失败", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "为热键打开宏编辑器失败: " + hotkey.name, e);
        }
    }

    /**
     * 构造 MacroEditor 期望的 JSON 结构：{"MACROS": {"0": {type,data}, ...}}
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
            Log.e(TAG, "构建宏容器JSON时出错", e);
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
            Log.e(TAG, "从宏容器JSON解析动作列表时出错", e);
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

    /** 简易输入对话框：用于输入/修改热键名称 */
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