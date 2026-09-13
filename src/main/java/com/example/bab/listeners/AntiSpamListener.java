package com.example.bab.listeners;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.checks.Toggleable;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiSpamListener implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Deque<Long>> messageTimestamps = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile long windowMs;
    private volatile int maxMessages;

    public AntiSpamListener(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("antispam.enabled", true);
        windowMs = plugin.getConfig().getLong("antispam.window-ms", 1000);
        maxMessages = plugin.getConfig().getInt("antispam.max-messages", 5);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = messageTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        boolean shouldBan;
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            shouldBan = timestamps.size() > maxMessages;
            if (shouldBan) {
                timestamps.clear();
            }
        }

        if (shouldBan) {
            String reason = plugin.getConfig().getString("antispam.ban-reason", "AntiSpam: gui tin nhan qua nhanh");
            String duration = plugin.getConfig().getString("antispam.ban-duration", "30d");
            // dispatchCommand phai chay tren main thread, con AsyncChatEvent chay bat dong bo
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getBanExecutor().banDirect(player, reason, duration));
        }
    }
}
