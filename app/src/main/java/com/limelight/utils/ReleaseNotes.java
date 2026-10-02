package com.limelight.utils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the user-facing, localized part of a GitHub release body. No Android dependencies. */
final class ReleaseNotes {
    // <!-- notes:zh-CN --> ... <!-- notes:en --> ... <!-- notes:end -->
    private static final Pattern NOTES_MARKER = Pattern.compile(
            "<!--\\s*notes:([A-Za-z-]+)\\s*-->");

    private ReleaseNotes() {
    }

    /**
     * Picks the user-facing part of a release body. Workflow-generated bodies carry one section
     * per language between {@code <!-- notes:xx -->} markers; the best match for the device
     * language wins (exact language+country, then same language, then English, then the first
     * section). Bodies without markers are used as-is minus the machine-readable lines.
     * Returns null when there is nothing worth showing.
     */
    static String select(String body, String language, String country) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        LinkedHashMap<String, String> sections = new LinkedHashMap<>();
        Matcher matcher = NOTES_MARKER.matcher(body);
        String currentKey = null;
        int sectionStart = 0;
        while (matcher.find()) {
            if (currentKey != null && !currentKey.equals("end")) {
                sections.put(currentKey, body.substring(sectionStart, matcher.start()));
            }
            currentKey = matcher.group(1).toLowerCase(Locale.ROOT);
            sectionStart = matcher.end();
        }
        if (currentKey != null && !currentKey.equals("end")) {
            sections.put(currentKey, body.substring(sectionStart));
        }

        String chosen;
        if (sections.isEmpty()) {
            chosen = body;
        }
        else {
            String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
            String full = country == null || country.isEmpty() ? lang : lang + "-" + country.toLowerCase(Locale.ROOT);
            chosen = sections.get(full);
            if (chosen == null) {
                chosen = firstSectionForLanguage(sections, lang);
            }
            if (chosen == null) {
                chosen = firstSectionForLanguage(sections, "en");
            }
            if (chosen == null) {
                chosen = sections.values().iterator().next();
            }
        }
        return clean(chosen);
    }

    private static String firstSectionForLanguage(Map<String, String> sections, String lang) {
        if (lang.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (entry.getKey().equals(lang) || entry.getKey().startsWith(lang + "-")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String clean(String text) {
        StringBuilder out = new StringBuilder();
        boolean lastBlank = true;
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.startsWith("#") || line.startsWith("<!--")
                    || line.toLowerCase(Locale.ROOT).startsWith("automated apk build from commit")) {
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = "\u2022 " + line.substring(2);
            }
            if (line.isEmpty()) {
                if (!lastBlank) {
                    out.append('\n');
                }
                lastBlank = true;
                continue;
            }
            out.append(line).append('\n');
            lastBlank = false;
        }
        String result = out.toString().trim();
        return result.isEmpty() ? null : result;
    }

}
