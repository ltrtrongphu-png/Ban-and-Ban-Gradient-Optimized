package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiem tra Timer - hack lam client gui goi tin di chuyen nhanh hon toc do tick
 * binh thuong cua server (20 tick/giay), giup nguoi choi hanh dong (di chuyen, dao,
 * an) nhanh hon binh thuong. Dem so luong PlayerMoveEvent trong 1 giay thuc te;
 * neu vuot qua nguong hop ly (co buffer cho lag/jitter) thi nghi ngo.
 *
 * FIX: ban cu dung "cua so co dinh" (dem tu 1 moc, reset moi 1000ms) - neu
 * client bi giat mang roi gui bu (burst) dung luc roi vao dau cua so moi, so
 * dem trong CHINH cua so do co the vuot nguong du toc do trung binh thuc te
 * hoan toan binh thuong (cua so truoc do co the gan nhu khong co goi tin nao
 * do dang cho). Sua sang sliding window bang Deque (dung pattern da co san o
 * FastInteractCheck.java trong cung codebase) - luon xet chinh xac 1000ms gan
 * nhat tinh tu HIEN TAI, khong con phu thuoc vao moc reset co dinh.
 */
public class TimerCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Deque<Long>> timestamps = new ConcurrentHashMap<>();

    public TimerCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile int maxMoves;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.timer.enabled", true);
        maxMoves = plugin.getConfig().getInt("anticheat.checks.timer.max-moves-per-second", 30);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isInsideVehicle()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> deque = timestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        synchronized (deque) {
            deque.addLast(now);
            long windowMs = 1000L;
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.pollFirst();
            }
            if (deque.size() > maxMoves) {
                plugin.flag(player, "timer", "Timer (goi tin: " + deque.size() + "/giay)");
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
