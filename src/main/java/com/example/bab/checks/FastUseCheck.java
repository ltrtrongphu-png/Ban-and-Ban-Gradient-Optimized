package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiem tra FastUse - phat hien an/uong (do an, thuoc, potion...) nhanh hon thoi gian
 * toi thieu ma client vanilla cho phep (an binh thuong mat khoang 1.6 giay/32 tick).
 * Hack "insta-eat" bo qua animation nay de an lien tuc khong delay.
 */
public class FastUseCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> lastConsume = new ConcurrentHashMap<>();

    public FastUseCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long minIntervalMs;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.fastuse.enabled", true);
        minIntervalMs = plugin.getConfig().getLong("anticheat.checks.fastuse.min-interval-ms", 1200);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastConsume.put(uuid, now);
        if (last == null) return;

        if (now - last < minIntervalMs) {
            plugin.flag(player, "fastuse", "FastUse (an/uong qua nhanh)");
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi lastConsume khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastConsume.remove(event.getPlayer().getUniqueId());
    }
}
