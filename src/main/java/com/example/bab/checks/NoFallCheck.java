package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phat hien "NoFall" - hack khu damage roi tu do bang cach gia mao trang thai onGround.
 * Cach lam: theo doi fallDistance khi nguoi choi dang roi; khi ho "cham dat" (onGround = true
 * theo client bao ve server), kiem tra xem co su kien EntityDamageEvent (FALL) tuong ung
 * duoc kich hoat trong vong vai tick sau khong. Neu fallDistance du lon ma khong co damage,
 * nghi ngo dung hack.
 */
public class NoFallCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    // Luu fallDistance tai thoi diem nghi ngo "cham dat", cho vai tick de xem co damage khong
    private final Map<UUID, Double> pendingCheck = new HashMap<>();
    private final Map<UUID, Boolean> tookFallDamage = new HashMap<>();

    public NoFallCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double minFallDistance;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.nofall.enabled", true);
        minFallDistance = plugin.getConfig().getDouble("anticheat.checks.nofall.min-fall-distance", 3.5);
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
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) return;
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING) || player.hasPotionEffect(PotionEffectType.LEVITATION)) return;

        UUID uuid = player.getUniqueId();

        // FIX (BUG): logic cu dung "isOnGround() && fallDistance==0 -> return som" de
        // tranh xu ly lai luc dang dung yen tren dat. Nhung dung ngay tick THUC SU cham
        // dat, Bukkit/Paper thuong DA reset fallDistance ve 0 truoc khi PlayerMoveEvent
        // toi tay plugin (dung nhu comment cu tu thua nhan) - nen guard nay vo tinh chan
        // luon nhanh else-if ben duoi, khien pendingCheck KHONG BAO GIO duoc tieu thu:
        // check tro thanh "chet", khong bao gio thuc su flag ai ca du dang co ghi nhan
        // pendingCheck luc roi.
        //
        // Sua: khong con dua vao gia tri fallDistance tai thoi diem cham dat nua (vi no
        // khong dang tin cay ve mat thu tu cap nhat). Chi dua vao canh CHUYEN TRANG THAI
        // isOnGround(): dang roi (!isOnGround(), tich luy pendingCheck) -> cham dat
        // (isOnGround()==true, chi can pendingCheck con entry la du de kich hoat kiem tra,
        // bat ke fallDistance luc nay da ve 0 hay chua).
        if (!player.isOnGround()) {
            float fallDistance = player.getFallDistance();
            if (fallDistance > minFallDistance) {
                // Dang roi, ghi nhan la co the se can kiem tra khi cham dat
                pendingCheck.put(uuid, (double) fallDistance);
            }
            return;
        }

        if (pendingCheck.containsKey(uuid)) {
            double recordedFall = pendingCheck.remove(uuid);
            tookFallDamage.put(uuid, false);

            new BukkitRunnable() {
                @Override
                public void run() {
                    Boolean tookDamage = tookFallDamage.remove(uuid);
                    if (tookDamage != null && !tookDamage) {
                        plugin.flag(player, "nofall", "NoFall (roi " + String.format("%.1f", recordedFall) + " block)");
                    }
                }
            }.runTaskLater(plugin, 3L);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            tookFallDamage.put(player.getUniqueId(), true);
        }
    }

    // FIX: NoClipCheck va MovementCheck trong cung codebase nay da co san grace
    // period sau PlayerTeleportEvent - NoFallCheck (mot trong 3 check goc gay
    // ban oan tu dau) lai thieu pattern nay. Neu pendingCheck dang ghi nhan 1
    // cu roi ma nguoi choi bi teleport (lenh /tp, plugin dau truong,
    // VerificationManager...) truoc khi cham dat, tick dau tien isOnGround()==true
    // SAU teleport se bi hieu nham la "cham dat tu cu roi cu" - nhung nguoi choi
    // chua bao gio thuc su roi o vi tri MOI nen khong the co fall damage tuong
    // ung -> bao dong gia. Xoa pendingCheck/tookFallDamage khi teleport, coi nhu
    // bat dau lai tu dau (giong lan dau join server).
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingCheck.remove(uuid);
        tookFallDamage.remove(uuid);
    }

    // FIX (memory leak): don entry cua nguoi choi khoi 2 map tren khi thoat server,
    // tranh phinh to vo han theo thoi gian tren server co nhieu nguoi choi ra vao.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingCheck.remove(uuid);
        tookFallDamage.remove(uuid);
    }
}
