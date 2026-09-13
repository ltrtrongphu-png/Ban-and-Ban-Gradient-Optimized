package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoTotem - phat hien nap lai Totem of Undying vao tay trai NHANH HON phan
 * xa con nguoi ngay sau khi totem vua duoc dung (mat/rong tay trai). "AutoTotem"
 * la 1 module pho bien trong cac client hack (bao gom Meteor Client) - tu dong
 * chuyen totem tu inventory ra tay trai trong <1 tick, trong khi thao tac
 * chuot/phim tay that can toi thieu vai chuc ms de phan ung + thuc hien click.
 *
 * Cach hoat dong: ghi nhan thoi diem tay trai vua CHUYEN TU totem SANG rong/khac
 * (dau hieu totem vua duoc tieu thu). Neu tay trai co totem TRO LAI trong thoi
 * gian ngan hon nguong hop ly (vd < 80ms) thong qua thao tac inventory (click/
 * swap - KHONG tinh truong hop server tu dong bu totem qua co che khac), flag.
 */
public class AutoTotemCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    // Thoi diem tay trai vua MAT totem gan nhat (uuid -> millis)
    private final Map<UUID, Long> offhandTotemLostAt = new ConcurrentHashMap<>();

    public AutoTotemCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long minRefillMs;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.autototem.enabled", true);
        minRefillMs = plugin.getConfig().getLong("anticheat.checks.autototem.min-refill-ms", 80);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        // Sat thuong (dac biet sat thuong lon/chi mang) la thoi diem totem THUONG
        // duoc tieu thu tu nhien - dung day de bat dau theo doi "tay trai vua rong".
        if (!(event.getEntity() instanceof Player player)) return;
        checkOffhandForTotemLoss(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        checkOffhandForTotemRefill(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        checkOffhandForTotemRefill(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        // Doi tick sau (0 tick = ngay lap tuc sau khi xu ly xong event nay) de
        // doc trang thai tay trai MOI NHAT, phong truong hop thu tu event doi khi lech.
        Player player = event.getPlayer();
        checkOffhandForTotemLoss(player);
    }

    private void checkOffhandForTotemLoss(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType() != Material.TOTEM_OF_UNDYING) {
            offhandTotemLostAt.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private void checkOffhandForTotemRefill(Player player) {
        if (!enabled) return;
        if (plugin.isExempt(player)) return;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType() != Material.TOTEM_OF_UNDYING) return;

        UUID uuid = player.getUniqueId();
        Long lostAt = offhandTotemLostAt.remove(uuid);
        if (lostAt == null) return; // tay trai da co totem tu truoc, khong phai vua "nap lai"

        long elapsed = System.currentTimeMillis() - lostAt;
        if (elapsed < minRefillMs) {
            plugin.flag(player, "autototem", "AutoTotem (nap lai totem sau " + elapsed + "ms)");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        offhandTotemLostAt.remove(event.getPlayer().getUniqueId());
    }
}
