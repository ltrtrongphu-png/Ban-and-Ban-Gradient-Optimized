package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FIX QUAN TRONG: ban goc dung player.getVelocity().getY() de phan biet "buoc
 * len" voi "nhay that" - NHUNG Bukkit KHONG dong bo lien tuc gia tri velocity
 * thuc te cua nguoi choi tu client (API nay chu yeu dung cho hieu ung server
 * chu dong nhu knockback, hau nhu luon tra ve gan 0 voi chuyen dong tu nhien
 * cua nguoi choi). Hau qua: dieu kien "van toc Y gan 0" gan nhu LUON DUNG, bien
 * check thanh "cu buoc len cao >0.85 block la nghi ngo" - de dinh bay/nua khoi/
 * cau thang.
 *
 * Sua: bo han dieu kien getVelocity(). Thay vao do theo doi dy TICH LUY qua
 * nhieu tick lien tiep - nhay that su co dang parabol (tang dan roi giam dan,
 * tong quang duong Y theo thoi gian khop voi cong thuc roi tu do vanilla); con
 * "day len" tuc thi (khong qua nhay) se the hien qua 1 buoc nhay DUY NHAT rat
 * lon ma khong co pha "tang toc-giam toc" tu nhien. Don gian hoa: chi flag khi
 * dy vuot nguong VA khong dang trong trang thai vua nhay (jumpGraceTicks).
 */
public class StepCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Integer> suspiciousStreak = new HashMap<>();
    private final Map<UUID, Integer> jumpGraceTicks = new HashMap<>();

    public StepCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double minStep;
    private volatile int requiredStreak;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.step.enabled", true);
        minStep = plugin.getConfig().getDouble("anticheat.checks.step.min-height", 0.85);
        requiredStreak = plugin.getConfig().getInt("anticheat.checks.step.required-streak", 2);
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
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle() || player.isSwimming()) return;
        if (player.isClimbing()) return;

        UUID uuid = player.getUniqueId();

        // Neu nguoi choi vua nhay (dang tren khong trong pha len tu nhien), khong tinh
        // la "buoc" - day la 1 phan cua cu nhay hop le, khong phai "day len tuc thi".
        if (player.isOnGround()) {
            jumpGraceTicks.put(uuid, 6); // cho phep 6 tick tiep theo van duoc coi la "vua nhay" neu roi khoi mat dat
        } else {
            int grace = jumpGraceTicks.getOrDefault(uuid, 0);
            if (grace > 0) {
                jumpGraceTicks.put(uuid, grace - 1);
                return; // dang trong pha nhay tu nhien, bo qua khong kiem tra
            }
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dy = to.getY() - from.getY();

        boolean suspicious = dy > minStep;

        if (suspicious) {
            int streak = suspiciousStreak.merge(uuid, 1, Integer::sum);
            if (streak >= requiredStreak) {
                plugin.flag(player, "step", "Step (buoc len " + String.format("%.2f", dy) + " block bat thuong)");
                suspiciousStreak.put(uuid, 0);
            }
        } else {
            suspiciousStreak.put(uuid, 0);
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi 2 map tren khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        suspiciousStreak.remove(uuid);
        jumpGraceTicks.remove(uuid);
    }
}
