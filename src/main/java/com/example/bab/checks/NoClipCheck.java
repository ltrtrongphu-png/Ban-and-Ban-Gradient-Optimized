package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kiem tra NoClip/Phase - phat hien nguoi choi ton tai ben trong khoi dac (tuong,
 * san, tran nha) ma khong phai do dang dao/dat block hoac do lag tam thoi. Client
 * vanilla that KHONG THE o vi tri nay vi va cham vat ly (collision box) luon ngan
 * client di chuyen vao khong gian bi chiem boi khoi dac.
 *
 * Co grace period sau teleport/damage giong MovementCheck de tranh bao sai do
 * server dich chuyen nguoi choi tam thoi vao vi tri chua kip cap nhat chunk.
 *
 * FIX (so sanh voi 1 plugin tham khao khac): truoc day dung 1 danh sach loai tru
 * tu viet tay (SIGN, CARPET, PRESSURE_PLATE...) de xac dinh khoi nao "khong thuc
 * su chan nguoi choi" - cach nay DE BO SOT loai khoi moi (vd item frame, chuoi,
 * day leo, hoa...). Sua: dung thang API Block#isPassable() co san cua Bukkit -
 * day la ham CHINH THUC server dung de tinh va cham that, xu ly dung MOI truong
 * hop khoi trong suot/khong chan ma khong can tu liet ke thu cong.
 */
public class NoClipCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Integer> insideSolidTicks = new HashMap<>();

    private volatile boolean enabled;
    private volatile int requiredTicks;

    public NoClipCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.noclip.enabled", true);
        requiredTicks = plugin.getConfig().getInt("anticheat.checks.noclip.required-ticks", 3);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        graceUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1000L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        graceUntil.put(player.getUniqueId(), System.currentTimeMillis() + 500L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isInsideVehicle() || player.isGliding() || player.isSwimming()) return;

        UUID uuid = player.getUniqueId();
        Long grace = graceUntil.get(uuid);
        if (grace != null && System.currentTimeMillis() < grace) {
            insideSolidTicks.remove(uuid);
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        boolean feetSolid = isTrulySolid(to.getWorld().getBlockAt(to));
        Location headLoc = to.clone().add(0, 1.3, 0);
        boolean headSolid = isTrulySolid(headLoc.getWorld().getBlockAt(headLoc));

        if (feetSolid && headSolid) {
            int ticks = insideSolidTicks.merge(uuid, 1, Integer::sum);
            if (ticks >= requiredTicks) {
                plugin.flag(player, "noclip", "NoClip (o trong khoi dac " + ticks + " tick)");
                insideSolidTicks.put(uuid, 0);
            }
        } else {
            insideSolidTicks.put(uuid, 0);
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi 2 map tren khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        graceUntil.remove(uuid);
        insideSolidTicks.remove(uuid);
    }

    private boolean isTrulySolid(Block block) {
        // isPassable() = true nghia la nguoi choi co the di xuyen qua (khong va
        // cham) - dung nguoc lai (!isPassable()) de xac dinh "thuc su chan duong".
        // Ham nay xu ly dung moi loai khoi (bien, tham, nut, day, hoa, item frame...)
        // ma khong can tu liet ke thu cong nhu truoc.
        return !block.isPassable();
    }
}
