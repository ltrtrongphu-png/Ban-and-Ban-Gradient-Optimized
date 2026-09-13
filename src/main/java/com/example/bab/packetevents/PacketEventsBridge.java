package com.example.bab.packetevents;

import com.example.bab.BaB;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

/**
 * Khoi tao vong doi cua thu vien PacketEvents (them song song voi ProtocolLib,
 * KHONG thay the - cac check cu van dung ProtocolLib nhu truoc). PacketEvents
 * dung cho cac check MOI can do chinh xac/on dinh giua nhieu phien ban Minecraft
 * tot hon (vi du: dem toc do goi tin dai/giay o tang thap, khong bi anh huong
 * boi cach Bukkit gom nhom event).
 *
 * QUAN TRONG VE VONG DOI - PacketEvents YEU CAU goi dung thu tu:
 *   1. load()  - phai goi trong onLoad() cua plugin chinh (TRUOC onEnable), vi
 *      no can gan injector vao pipeline netty som nhat co the.
 *   2. init()  - goi trong onEnable().
 *   3. terminate() - goi trong onDisable().
 * Neu goi sai thu tu (vi du init() ma chua load()) thu vien se nem loi ngay.
 */
public final class PacketEventsBridge {

    private static boolean loaded = false;

    private PacketEventsBridge() {
    }

    /** Goi trong onLoad() cua BaB, TRUOC MOI THU KHAC. */
    public static void load(BaB plugin) {
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
            PacketEvents.getAPI().load();
            loaded = true;
        } catch (Throwable t) {
            // Khong co jar PacketEvents tren classpath (chua duoc them/shade vao build) -
            // vo hieu hoa an toan cac check dua tren PacketEvents, cac check ProtocolLib
            // khac cua BaB van chay binh thuong.
            loaded = false;
            plugin.getLogger().warning("[BaB] Khong khoi tao duoc PacketEvents - cac check dua tren no se bi tat. Loi: " + t);
        }
    }

    /** Goi trong onEnable() cua BaB, sau khi da goi load() o onLoad(). */
    public static void init() {
        if (!loaded) return;
        PacketEvents.getAPI().init();
    }

    /** Goi trong onDisable() cua BaB. */
    public static void terminate() {
        if (!loaded) return;
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable ignored) {
            // Server dang tat/reload, bo qua loi terminate an toan.
        }
    }

    public static boolean isAvailable() {
        return loaded;
    }
}
