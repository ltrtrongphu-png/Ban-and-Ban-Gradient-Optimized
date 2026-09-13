package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX: nhieu khoi vanilla vo TUC THI (0 tick) voi BAT KY cong cu nao (la cay,
 * co, day leo, co bien, hoa...) - khi nguoi choi chay xuyen tan la rung (rat
 * binh thuong khi chat cay/don duong), moi tick server co the vo 1 la MOI
 * khac nhau, nhip phai gan sat hoac duoi nguong min-break-interval-ms mac
 * dinh (50ms = 1 tick) chi do jitter goi tin thong thuong - gay bao dong gia
 * Nuker cho hanh vi hoan toan tu nhien.
 *
 * Sua: dung Material#getHardness() (co san trong Bukkit API, tra ve 0 cho moi
 * khoi vo tuc thi) de loai tru, thay vi tu liet ke danh sach thu cong (de bo
 * sot khoi moi) - dung tinh than voi cach NoClipCheck da dung Block#isPassable().
 */
public class FastBreakCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> lastBreak = new ConcurrentHashMap<>();

    public FastBreakCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long minIntervalMs;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.fastbreak.enabled", true);
        minIntervalMs = plugin.getConfig().getLong("anticheat.checks.fastbreak.min-break-interval-ms", 50);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (!enabled) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        long now = System.currentTimeMillis();
        Long last = lastBreak.put(player.getUniqueId(), now);
        if (last == null) return;

        // FIX: khoi vo tuc thi (hardness <= 0) khong co gia tri chan doan cho
        // khoang cach thoi gian - bo qua khong flag, nhung van cap nhat
        // lastBreak o tren de moc thoi gian dung cho lan dao khoi THUONG tiep theo.
        if (event.getBlock().getType().getHardness() <= 0f) return;

        if (now - last < minIntervalMs) {
            plugin.flag(player, "fastbreak", "FastBreak/Nuker");
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi lastBreak khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastBreak.remove(event.getPlayer().getUniqueId());
    }
}
