package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kiem tra AutoArmor (moi, tham khao tu 1 plugin khac) - phat hien thay doi
 * giap qua nhanh/lien tuc nhieu lan (vd "tu dong mac giap tot nhat" ma khong
 * can nguoi choi thao tac tay). Dung PlayerArmorChangeEvent (co san tren Paper)
 * de theo doi moi lan giap thay doi, bat ke qua click chuot, shift-click, hay
 * bat ky co che nao khac.
 */
public class AutoArmorCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> lastChange = new HashMap<>();
    private final Map<UUID, Integer> rapidCount = new HashMap<>();

    public AutoArmorCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long minIntervalMs;
    private volatile int requiredCount;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.autoarmor.enabled", true);
        minIntervalMs = plugin.getConfig().getLong("anticheat.checks.autoarmor.min-interval-ms", 150);
        requiredCount = plugin.getConfig().getInt("anticheat.checks.autoarmor.required-count", 3);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long last = lastChange.put(uuid, now);
        if (last != null && now - last < minIntervalMs) {
            int count = rapidCount.merge(uuid, 1, Integer::sum);
            if (count >= requiredCount) {
                plugin.flag(player, "autoarmor", "AutoArmor (thay giap qua nhanh lien tuc)");
                rapidCount.put(uuid, 0);
            }
        } else {
            rapidCount.put(uuid, 0);
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi 2 map tren khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastChange.remove(uuid);
        rapidCount.remove(uuid);
    }
}
