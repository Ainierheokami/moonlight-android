package com.limelight.heokami;
import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TemplateRenderer {
    /**
     * 渲染模板，将模板中的占位符替换为指定的数据。
     *
     * @param template 模板字符串，包含占位符（如 "@data1"）。
     * @param data     替换占位符的键值对映射，键是占位符（如 "@data1"），值是替换内容。
     * @return 渲染后的字符串，所有占位符被替换为实际值。
     */
    @SuppressLint("NewApi")
    public static String render(String template, Map<String, String> data) {
        if (template == null || data == null || data.isEmpty()) {
            return template; // 如果模板或数据为空，直接返回模板
        }

        // 将数据按键的长度从长到短排序
        List<Map.Entry<String, String>> entries = new ArrayList<>(data.entrySet());
        entries.sort((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()));

        // 遍历数据，将占位符替换为对应值
        for (Map.Entry<String, String> entry : entries) {
            String placeholder = entry.getKey(); // 占位符，如 "@data1"
            String value = entry.getValue(); // 替换值，如 "123 fps"
            template = template.replace(placeholder, value);
        }

        return template;
    }
}
