package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiem tra FastInteract (moi, tham khao tu 1 plugin khac) - phat hien tan
 * suat tuong tac (click chuot phai vao block/vat pham) vuot kha nang con
 * nguoi trong 1 khoang thoi gian ngan.
 */
public class FastInteractCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Deque<Long>> timestamps = new ConcurrentHashMap<>();

    public FastInteractCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long windowMs;
    private volatile int maxPerWindow;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.fastinteract.enabled", true);
        windowMs = plugin.getConfig().getLong("anticheat.checks.fastinteract.window-ms", 1000);
        maxPerWindow = plugin.getConfig().getInt("anticheat.checks.fastinteract.max-per-window", 15);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() == Action.PHYSICAL) return;
        // FIX: PlayerInteractEvent co the ban ra 2 lan cho DUNG 1 cu click that
        // (tay chinh + tay phu) neu nguoi choi dang cam item o tay trai (khien,
        // ban do, totem...). Khong loc hand se dem gap doi toc do tuong tac
        // that cua nguoi choi hop le, dan toi bao dong gia khi cham nguong
        // max-per-window nhanh gap 2 lan toc do tay that.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> deque = timestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        synchronized (deque) {
            deque.addLast(now);
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.pollFirst();
            }
            if (deque.size() > maxPerWindow) {
                plugin.flag(player, "fastinteract", "FastInteract (" + deque.size() + " tuong tac/giay)");
                deque.clear();
            }
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi timestamps khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        timestamps.remove(event.getPlayer().getUniqueId());
    }
}
