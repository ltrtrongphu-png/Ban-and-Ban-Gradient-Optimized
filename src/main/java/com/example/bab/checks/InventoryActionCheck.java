package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX: ban goc flag ngay khi getOpenInventory().getType() != CRAFTING, KHONG co
 * khoang dem thoi gian nao sau khi dong GUI. Do do tre mang (dac biet ping cao),
 * server co the chua kip cap nhat trang thai "da dong ruong/lo nung" trong 1-2
 * tick, khien nguoi choi dong ruong xong dao/dat block tiep ngay lap tuc (rat
 * pho bien) bi flag oan. Vi weight=40 rat cao, chi 2 lan la du nguong ban.
 *
 * Sua: them khoang dem 300ms sau su kien InventoryCloseEvent - bo qua check
 * trong khoang thoi gian nay de tranh dung nham tinh huong dong GUI xong lam
 * viec tiep ngay.
 */
public class InventoryActionCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> closeGraceUntil = new ConcurrentHashMap<>();

    public InventoryActionCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile long graceMs;
    private volatile boolean enabled;

    @Override
    public void loadConfigValues() {
        graceMs = plugin.getConfig().getLong("anticheat.checks.invaction.close-grace-ms", 300L);
        enabled = plugin.getConfig().getBoolean("anticheat.checks.invaction.enabled", true);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        closeGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + graceMs);
    }

    private boolean hasGuiOpen(Player player) {
        Long grace = closeGraceUntil.get(player.getUniqueId());
        if (grace != null && System.currentTimeMillis() < grace) {
            return false; // dang trong khoang dem sau khi dong GUI -> khong tinh la "dang mo"
        }
        InventoryType type = player.getOpenInventory().getType();
        return type != InventoryType.CRAFTING;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (hasGuiOpen(player)) {
            plugin.flag(player, "invaction", "InventoryAction (dao block khi dang mo GUI)");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (hasGuiOpen(player)) {
            plugin.flag(player, "invaction", "InventoryAction (dat block khi dang mo GUI)");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        Entity entity = event.getDamager();
        if (!(entity instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (plugin.isExempt(player)) return;
        if (hasGuiOpen(player)) {
            plugin.flag(player, "invaction", "InventoryAction (danh nguoi/quai khi dang mo GUI)");
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi closeGraceUntil khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        closeGraceUntil.remove(event.getPlayer().getUniqueId());
    }
}
