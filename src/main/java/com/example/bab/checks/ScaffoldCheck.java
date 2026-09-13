package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX: ban goc flag khi goc lech giua huong nhin va vi tri dat block > 75 do.
 * Day la BUG NGHIEM TRONG vi no trung y het hanh vi BAC CAU BINH THUONG: khi
 * bac cau, nguoi choi thuong nhin ve PHIA TRUOC trong khi dat block PHIA SAU/
 * DUOI CHAN minh - goc lech nay thuong xuyen VUOT XA 75 do ngay ca khi choi
 * hoan toan hop le. Hau qua: hau nhu ai bac cau/xay nha deu bi flag oan.
 *
 * Sua: bo han check goc (khong the lam dung ma khong gay bao sai voi ky thuat
 * don gian). Chi giu lai check khoang thoi gian giua 2 lan dat block - day la
 * tin hieu DUY NHAT con lai dang tin cay (vanilla co gioi han toc do tuong tac
 * ro rang, dat block nhanh hon muc do KHONG THE lam duoc bang tay that).
 */
public class ScaffoldCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> lastPlace = new ConcurrentHashMap<>();

    public ScaffoldCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long minIntervalMs;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.scaffold.enabled", true);
        minIntervalMs = plugin.getConfig().getLong("anticheat.checks.scaffold.min-place-interval-ms", 40L);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        checkInterval(player);
    }

    private void checkInterval(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastPlace.put(uuid, now);
        if (last == null) return;

        if (now - last < minIntervalMs) {
            plugin.flag(player, "scaffold", "Scaffold (dat block qua nhanh)");
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi lastPlace khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPlace.remove(event.getPlayer().getUniqueId());
    }
}
