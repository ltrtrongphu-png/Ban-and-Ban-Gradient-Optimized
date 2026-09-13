package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FIX QUAN TRONG: ban goc flag ngay khi nguoi choi dang len (dy > 0.06) VA co
 * khoi dac ngay ben canh - nhung day CHINH XAC la tinh huong xay ra khi nhay
 * binh thuong trong khong gian hep (hanh lang, chuong mob, cau thang hep, tru
 * trang tri...) - cuc ky pho bien trong build server. Pha len cua 1 cu nhay
 * tu nhien keo dai ~4-5 tick, du de kich hoat "required-ticks: 4" ngay ca khi
 * khong he leo tuong gi ca.
 *
 * Sua: them "grace" sau khi roi mat dat (giong StepCheck) - chi tinh la nghi
 * ngo neu nguoi choi da o tren khong QUA LAU (vuot xa thoi gian 1 cu nhay binh
 * thuong keo dai, ~12 tick) MA VAN tiep tuc len - day moi la dau hieu that su
 * bat thuong (nhay binh thuong khong the "len mai" duoc, chi len roi phai roi
 * xuong do trong luc).
 */
public class SpiderCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Integer> suspiciousTicks = new HashMap<>();
    private final Map<UUID, Integer> airborneTicks = new HashMap<>();

    public SpiderCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile int naturalJumpTicks;
    private volatile int requiredTicks;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.spider.enabled", true);
        naturalJumpTicks = plugin.getConfig().getInt("anticheat.checks.spider.natural-jump-ticks", 12);
        requiredTicks = plugin.getConfig().getInt("anticheat.checks.spider.required-ticks", 4);
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
        if (player.isClimbing()) return; // Thang/day leo hop le

        UUID uuid = player.getUniqueId();

        if (player.isOnGround()) {
            airborneTicks.put(uuid, 0);
            suspiciousTicks.put(uuid, 0);
            return;
        }
        int airTicks = airborneTicks.merge(uuid, 1, Integer::sum);

        // FIX: 1 cu nhay vanilla binh thuong keo dai toi da ~12 tick tu luc roi
        // dat den luc cham dat lai (voi jump height mac dinh). Duoi nguong nay,
        // dang len trong khong gian hep VAN LA NHAY BINH THUONG, khong phai leo
        // tuong - bo qua hoan toan, khong tich luy suspiciousTicks.
        if (airTicks <= naturalJumpTicks) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dy = to.getY() - from.getY();
        boolean ascendingWhileAirborne = dy > 0.06;
        boolean againstWall = isAgainstWall(player);

        if (ascendingWhileAirborne && againstWall) {
            int ticks = suspiciousTicks.merge(uuid, 1, Integer::sum);
            if (ticks >= requiredTicks) {
                plugin.flag(player, "spider", "Spider (leo tuong bat thuong, da tren khong " + airTicks + " tick)");
                suspiciousTicks.put(uuid, 0);
            }
        } else {
            suspiciousTicks.put(uuid, 0);
        }
    }

    // FIX: cung 1 lo hong nhu NoFallCheck - MovementCheck/NoClipCheck trong
    // codebase nay da co grace period sau teleport, SpiderCheck (1 trong 3
    // check goc gay ban oan) lai thieu. Neu airborneTicks da gan cham nguong
    // natural-jump-ticks roi nguoi choi bi teleport, so tick "lo lung" cu se
    // dong tiep sau khi den noi moi, khien "han muc nhay tu nhien" cho cu nhay
    // DAU TIEN o vi tri moi bi rut ngan bat cong - de flag oan Spider ngay ca
    // khi ho chi vua nhay 1 cai binh thuong sau khi vua duoc dua toi.
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        airborneTicks.remove(uuid);
        suspiciousTicks.remove(uuid);
    }

    // FIX (memory leak): don entry cua nguoi choi khoi 2 map tren khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        suspiciousTicks.remove(uuid);
        airborneTicks.remove(uuid);
    }

    private boolean isAgainstWall(Player player) {
        Location loc = player.getLocation();
        Block[] neighbors = {
                loc.clone().add(1, 0.5, 0).getBlock(),
                loc.clone().add(-1, 0.5, 0).getBlock(),
                loc.clone().add(0, 0.5, 1).getBlock(),
                loc.clone().add(0, 0.5, -1).getBlock()
        };
        for (Block b : neighbors) {
            if (!b.isPassable()) return true;
        }
        return false;
    }
}
