package com.example.bab.verification;

import com.example.bab.BaB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VerificationListener implements Listener {

    private final BaB plugin;

    public VerificationListener(BaB plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getVerificationManager().beginVerification(event.getPlayer());
        plugin.getHackDetectionManager().scheduleAutoCheckIfEnabled(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!plugin.getVerificationManager().isPending(event.getPlayer().getUniqueId())) return;
        plugin.getVerificationManager().onMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    // FIX (memory leak): ViolationManager va EscalationManager truoc day khong he
    // duoc don theo UUID khi nguoi choi thoat (khac voi VerificationManager/
    // ReplayRecorder/ReplayPlaybackManager da duoc xu ly dung ngay ben duoi).
    // Ket qua: 2 manager nay phinh to vo han theo thoi gian tren server co nhieu
    // nguoi choi ra vao. Don ngay tai day cho nhat quan voi cac manager con lai.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getVerificationManager().cancelPending(uuid);
        plugin.getReplayRecorder().onQuit(uuid);
        plugin.getReplayPlaybackManager().forceStop(uuid);
        plugin.getViolationManager().resetAll(uuid);
        plugin.getEscalationManager().resetPlayer(uuid);
    }

    private boolean isPending(Player player) {
        return plugin.getVerificationManager().isPending(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isPending(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isPending(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isPending(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cVui long hoan tat xac minh truoc.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isPending(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isPending(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isPending(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isPending(player)) {
            event.setCancelled(true);
        }
    }
}
