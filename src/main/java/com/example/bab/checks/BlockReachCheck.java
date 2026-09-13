package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Kiem tra BlockReach (moi, tham khao tu 1 plugin khac) - phat hien tuong tac
 * (dao/dat/click) BAT KY loai block nao tu khoang cach xa hon binh thuong.
 * Rong hon ContainerReachCheck da co (chi ap dung cho container) - bat cac
 * hack "reach" tong quat hon, khong gioi han o mo ruong.
 */
public class BlockReachCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;

    public BlockReachCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double maxReach;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.blockreach.enabled", true);
        maxReach = plugin.getConfig().getDouble("anticheat.checks.blockreach.max-distance", 6.0);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private boolean shouldCheck(Player player) {
        if (!enabled) return false;
        if (plugin.isExempt(player)) return false;
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private void checkDistance(Player player, Block block, String actionName) {
        double distance = player.getEyeLocation().distance(block.getLocation().add(0.5, 0.5, 0.5));
        if (distance > maxReach) {
            plugin.flag(player, "blockreach", "BlockReach (" + actionName + " tu " + String.format("%.1f", distance) + " block)");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!shouldCheck(player)) return;
        checkDistance(player, event.getBlock(), "dao");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldCheck(player)) return;
        checkDistance(player, event.getBlock(), "dat");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // FIX: cung 1 quirk da duoc ContainerReachCheck xu ly - PlayerInteractEvent
        // ban ra 2 lan cho 1 cu click that neu co item o tay trai, khien 1 lan
        // click bien tho o ria nguong max-distance bi cong VL 2 lan thay vi 1.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!shouldCheck(player)) return;
        if (event.getClickedBlock() == null) return;
        checkDistance(player, event.getClickedBlock(), "click");
    }
}
