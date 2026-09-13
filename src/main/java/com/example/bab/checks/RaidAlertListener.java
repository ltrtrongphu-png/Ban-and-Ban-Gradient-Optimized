package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CHI GHI LOG/CANH BAO, KHONG TU DONG PHAT - day khac voi cac check con lai.
 *
 * Theo doi mau hinh: nguoi choi dung yen (khong di chuyen/xoay goc nhin dang ke)
 * mot khoang thoi gian bat thuong dai, roi ngay lap tuc mo mot container quy gia
 * (ender chest/shulker box). Day CO THE la dau hieu ho dang dung FreeCam/ESP de
 * "do tham" truoc khi hanh dong (vi FreeCam khong gui goi tin di chuyen trong luc
 * dang quan sat) - nhung CUNG CO THE chi la nguoi choi dang doc chat, alt-tab, di
 * ve sinh... Vi qua nhieu kha nang bao sai, day CHI la cong cu ho tro staff dieu
 * tra khi co khieu nai raid, KHONG duoc dung de tu dong ket luan/phat nguoi choi.
 */
public class RaidAlertListener implements Listener {

    private static final Set<Material> HIGH_VALUE_CONTAINERS = EnumSet.of(
            Material.ENDER_CHEST, Material.SHULKER_BOX
    );

    private final BaB plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public RaidAlertListener(BaB plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().distanceSquared(event.getTo()) < 0.0004
                && event.getFrom().getYaw() == event.getTo().getYaw()
                && event.getFrom().getPitch() == event.getTo().getPitch()) {
            return; // Khong tinh la "hoat dong" neu khong thuc su di chuyen/xoay goc
        }
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("anticheat.checks.raidalert.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();
        boolean isShulker = type.name().endsWith("SHULKER_BOX");
        if (!HIGH_VALUE_CONTAINERS.contains(type) && !isShulker) return;

        UUID uuid = player.getUniqueId();
        Long last = lastActivity.get(uuid);
        long now = System.currentTimeMillis();
        long idleMs = last == null ? Long.MAX_VALUE : now - last;

        long thresholdMs = plugin.getConfig().getLong("anticheat.checks.raidalert.idle-threshold-seconds", 20) * 1000L;
        if (idleMs >= thresholdMs) {
            String permission = plugin.getConfig().getString("settings.alert-permission", "bab.alerts");
            String msg = ChatColor.GOLD + "[BaB] " + ChatColor.GRAY + "Luu y dieu tra (KHONG phai bang chung chac chan): "
                    + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " dung yen ~" + (idleMs / 1000)
                    + "s roi mo " + type.name().toLowerCase() + " ngay lap tuc.";
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(permission)) staff.sendMessage(msg);
            }
            plugin.getLogger().info("[RaidAlert] " + player.getName() + " idle " + (idleMs / 1000) + "s -> " + type);
        }

        lastActivity.put(uuid, now);
    }

    // FIX (memory leak): don entry cua nguoi choi khoi lastActivity khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
    }
}
