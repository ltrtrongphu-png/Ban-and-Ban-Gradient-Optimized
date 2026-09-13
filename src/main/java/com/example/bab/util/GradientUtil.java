package com.example.bab.util;

import net.md_5.bungee.api.ChatColor;

/**
 * Tien ich tao chu gradient (chuyen sac) bang ma mau hex cho cac tin nhan
 * cua plugin (title/subtitle/actionbar/chat). Yeu cau server 1.16+ vi dung
 * ma mau hex (&#RRGGBB) thay vi ma mau cu.
 */
public final class GradientUtil {

    private GradientUtil() {
    }

    /**
     * Tao gradient 2 mau cho 1 doan text.
     * VD: gradient("XAC MINH", "#FF0000", "#FFFF00")
     */
    public static String gradient(String text, String startHex, String endHex) {
        return gradient(text, new String[]{startHex, endHex});
    }

    /**
     * Gradient nhieu diem mau (VD: do -> cam -> vang).
     * Can toi thieu 2 ma mau trong hexStops.
     */
    public static String gradient(String text, String... hexStops) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (hexStops == null || hexStops.length < 2) {
            return text;
        }

        int segments = hexStops.length - 1;
        int len = text.length();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(' ');
                continue;
            }

            float progress = len <= 1 ? 0 : (float) i / (len - 1);
            int segIndex = Math.min((int) (progress * segments), segments - 1);
            float localRatio = (progress * segments) - segIndex;

            result.append(interpolateChar(c, hexStops[segIndex], hexStops[segIndex + 1], localRatio));
        }
        return result.toString();
    }

    private static String interpolateChar(char c, String startHex, String endHex, float ratio) {
        int startR = Integer.parseInt(startHex.substring(1, 3), 16);
        int startG = Integer.parseInt(startHex.substring(3, 5), 16);
        int startB = Integer.parseInt(startHex.substring(5, 7), 16);
        int endR = Integer.parseInt(endHex.substring(1, 3), 16);
        int endG = Integer.parseInt(endHex.substring(3, 5), 16);
        int endB = Integer.parseInt(endHex.substring(5, 7), 16);

        int r = (int) (startR + ratio * (endR - startR));
        int g = (int) (startG + ratio * (endG - startG));
        int b = (int) (startB + ratio * (endB - startB));

        String hex = String.format("#%02X%02X%02X", r, g, b);
        return ChatColor.of(hex) + String.valueOf(c);
    }
}
