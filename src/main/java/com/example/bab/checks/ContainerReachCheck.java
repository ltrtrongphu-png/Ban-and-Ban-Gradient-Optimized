package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Set;

/**
 * Kiem tra ContainerReach - phat hien mo ruong/container (chest, ender chest,
 * shulker box, barrel, furnace, trapped chest...) tu khoang cach xa hon binh
 * thuong. Mot so exploit cho phep "hut do" hoac mo container xuyen tuong ma
 * khong can dao pha - rat lien quan toi raid base vi ke gian co the loot do ma
 * chu nha khong he biet tuong da bi "xuyen qua".
 *
 * Khac voi FreeCam/ESP (chi la hieu ung hien thi, khong gui goi tin gi khac
 * thuong), hanh dong MO CONTAINER la mot su kien server-side that su, nen kiem
 * tra khoang cach o day hoan toan dang tin cay, khong phai suy doan.
 */
public class ContainerReachCheck implements Listener, ConfigReloadable, Toggleable {

    private static final Set<Material> CONTAINERS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.BARREL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.SHULKER_BOX, Material.DISPENSER, Material.DROPPER, Material.HOPPER,
            Material.BREWING_STAND
    );

    private final BaB plugin;

    public ContainerReachCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double maxReach;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.containerreach.enabled", true);
        maxReach = plugin.getConfig().getDouble("anticheat.checks.containerreach.max-distance", 6.0);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        boolean isShulker = type.name().endsWith("SHULKER_BOX");
        if (!CONTAINERS.contains(type) && !isShulker) return;

        double distance = player.getEyeLocation().distance(block.getLocation().add(0.5, 0.5, 0.5));

        if (distance > maxReach) {
            plugin.flag(player, "containerreach",
                    "ContainerReach (mo " + type.name().toLowerCase() + " tu " + String.format("%.1f", distance) + " block)");
        }
    }
}
