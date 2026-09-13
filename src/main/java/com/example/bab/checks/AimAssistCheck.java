package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kiem tra AimAssist (moi, tham khao tu 1 plugin khac) - phat hien goc nhin
 * "snap" (nhay giat) dot ngot ve huong 1 muc tieu ngay truoc/trong luc tan cong -
 * dau hieu dac trung cua killaura kieu "aim-assist" (chuot tu dong khoa muc
 * tieu). Nguoi choi that xoay camera muot ma theo thoi gian; hack loai nay
 * thuong nhay thang toi vi tri muc tieu chi trong 1-2 tick.
 */
public class AimAssistCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Float> lastYaw = new HashMap<>();

    public AimAssistCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double minSnapDegrees;
    private volatile double alignmentThreshold;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.aimassist.enabled", true);
        minSnapDegrees = plugin.getConfig().getDouble("anticheat.checks.aimassist.min-snap-degrees", 35.0);
        alignmentThreshold = plugin.getConfig().getDouble("anticheat.checks.aimassist.alignment-dot", 0.97);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        // Chi luu lai yaw gan nhat (khong flag o day) de dung khi co su kien tan cong
        lastYaw.put(player.getUniqueId(), to.getYaw());
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        Float previousYaw = lastYaw.get(uuid);
        float currentYaw = player.getLocation().getYaw();
        lastYaw.put(uuid, currentYaw);
        if (previousYaw == null) return;

        float delta = Math.abs(currentYaw - previousYaw);
        if (delta > 180) delta = 360 - delta;

        if (delta < minSnapDegrees) return;

        // Sau khi "nhay", goc nhin co dang khoa chinh xac vao muc tieu khong?
        Vector toTarget = target.getLocation().add(0, target.getHeight() / 2.0, 0)
                .toVector().subtract(player.getEyeLocation().toVector()).normalize();
        Vector look = player.getEyeLocation().getDirection().normalize();
        double dot = look.dot(toTarget);

        if (dot >= alignmentThreshold) {
            plugin.flag(player, "aimassist", "AimAssist (xoay " + String.format("%.0f", delta) + " do, khoa chinh xac muc tieu)");
        }
    }

    // FIX: PlayerTeleportEvent (lenh /tp, plugin duel/arena, VerificationManager
    // dua nguoi choi ra khoi limbo...) KHONG kich hoat PlayerMoveEvent, nen
    // lastYaw se giu goc nhin TRUOC khi teleport cho toi khi co goi di chuyen
    // tu nhien tiep theo. Neu nguoi choi tan cong ngay sau teleport (rat binh
    // thuong), delta yaw do duoc la do CHENH LECH GIUA 2 THE GIOI/VI TRI KHAC
    // NHAU, khong phai "snap" that - gay bao dong gia. Xoa lastYaw de lan tan
    // cong dau tien sau teleport duoc bo qua (giong nhu lan dau tien join).
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        lastYaw.remove(event.getPlayer().getUniqueId());
    }

    // FIX (memory leak): don entry cua nguoi choi khoi lastYaw khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastYaw.remove(event.getPlayer().getUniqueId());
    }
}
