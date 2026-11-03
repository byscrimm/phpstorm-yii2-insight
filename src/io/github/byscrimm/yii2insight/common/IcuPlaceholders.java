package io.github.byscrimm.yii2insight.common;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Argument names of an ICU MessageFormat pattern as used by {@code Yii::t()}: {@code {name}},
 * {@code {n, plural, =0{none} other{# of {total}}}}. Quoted text ({@code '{'}) is literal.
 * Returns null for an unbalanced pattern so callers do not diagnose it.
 */
public final class IcuPlaceholders {
    private IcuPlaceholders() {}

    public static Set<String> of(String pattern) {
        Set<String> names = new LinkedHashSet<>();
        return pattern != null && message(pattern, 0, names, 0) == pattern.length() ? names : null;
    }

    /** Parses message text until an unmatched '}' or the end; returns the stop index or -1. */
    private static int message(String s, int i, Set<String> names, int depth) {
        if (depth > 16) return -1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'') { i = quoted(s, i); if (i < 0) return -1; continue; }
            if (c == '}') return i;
            if (c == '{') { i = argument(s, i + 1, names, depth); if (i < 0) return -1; continue; }
            i++;
        }
        return i;
    }

    /** {@code name}, {@code name, type} or {@code name, plural|select|selectordinal, options}; returns index after '}'. */
    private static int argument(String s, int i, Set<String> names, int depth) {
        int start = i;
        while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}') i++;
        if (i >= s.length()) return -1;
        String name = s.substring(start, i).trim();
        if (name.isEmpty() || !name.matches("\\w+")) return -1;
        names.add(name);
        if (s.charAt(i) == '}') return i + 1;
        int typeStart = ++i;
        while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}') i++;
        if (i >= s.length()) return -1;
        String type = s.substring(typeStart, i).trim();
        if (s.charAt(i) == '}') return i + 1;
        i++;
        if (!type.equals("plural") && !type.equals("select") && !type.equals("selectordinal")) {
            // Style text such as {n, number, integer} or {d, date, short}: skip to the matching brace.
            int level = 0;
            for (; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '{') level++;
                else if (c == '}') { if (level == 0) return i + 1; level--; }
            }
            return -1;
        }
        // Options: selector{message} pairs, then the closing brace.
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '}') return i + 1;
            if (c == '{') {
                i = message(s, i + 1, names, depth + 1);
                if (i < 0 || i >= s.length()) return -1;
                i++;
                continue;
            }
            i++;
        }
        return -1;
    }

    /** {@code ''} is an apostrophe; {@code '...'} quotes syntax characters. Returns the index after the quote. */
    private static int quoted(String s, int i) {
        if (i + 1 < s.length() && s.charAt(i + 1) == '\'') return i + 2;
        if (i + 1 >= s.length() || "{}#|".indexOf(s.charAt(i + 1)) < 0) return i + 1;
        int end = s.indexOf('\'', i + 1);
        return end < 0 ? s.length() : end + 1;
    }
}
