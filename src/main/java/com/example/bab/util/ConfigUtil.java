package com.example.bab.util;

import com.example.bab.BaB;

/**
 * SUGGESTION (doc E "Validate cac gia tri config"): nhieu noi dung getDouble/getInt
 * doc config voi 1 gia tri mac dinh nhung khong kiem tra admin co lo nhap gia tri
 * am/vo ly hay khong (vi du weight am, min-tps am, platform-size = -5...). Cac ham
 * o day doc gia tri, kiem tra no co nam trong [min, max] hop ly hay khong; neu
 * khong, ghi log canh bao va tra ve gia tri mac dinh thay vi de gia tri vo ly do
 * thang vao logic (co the gay vong lap vo han, chia cho 0, hanh vi khong xac dinh...).
 *
 * Da ap dung cho cac config quan trong nhat (anh huong truc tiep den logic cham
 * diem/bu tru TPS/kich thuoc the gioi limbo/bo dem replay...); co the mo rong them
 * cho cac check con lai theo cung 1 pattern nay neu can.
 */
public final class ConfigUtil {

    private ConfigUtil() {
    }

    public static double getBoundedDouble(BaB plugin, String path, double def, double min, double max) {
        double value = plugin.getConfig().getDouble(path, def);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            plugin.getLogger().warning("[BaB] config '" + path + "' = " + value
                    + " nam ngoai khoang hop le [" + min + ", " + max + "] - dung gia tri mac dinh " + def + ".");
            return def;
        }
        return value;
    }

    public static int getBoundedInt(BaB plugin, String path, int def, int min, int max) {
        int value = plugin.getConfig().getInt(path, def);
        if (value < min || value > max) {
            plugin.getLogger().warning("[BaB] config '" + path + "' = " + value
                    + " nam ngoai khoang hop le [" + min + ", " + max + "] - dung gia tri mac dinh " + def + ".");
            return def;
        }
        return value;
    }

    public static long getBoundedLong(BaB plugin, String path, long def, long min, long max) {
        long value = plugin.getConfig().getLong(path, def);
        if (value < min || value > max) {
            plugin.getLogger().warning("[BaB] config '" + path + "' = " + value
                    + " nam ngoai khoang hop le [" + min + ", " + max + "] - dung gia tri mac dinh " + def + ".");
            return def;
        }
        return value;
    }
}
