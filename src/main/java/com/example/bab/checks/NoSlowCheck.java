package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX (ban dau): ban goc dung player.isHandRaised() de xac dinh "phai bi cham
 * lai" - nhung ham nay CO THE tra ve true sai trong vai truong hop canh (ngu
 * giuong/o thuyen tren mot so phien ban Paper) khong lam giam toc do di chuyen
 * thuc su - nen ban do da RUT XUONG chi con kiem tra isBlocking() (khien) de
 * an toan tuyet doi.
 *
 * NANG CAP (quan trong - sua lo hong bo sot): theo Minecraft Wiki (Slowness/
 * "alternative methods of reducing travel speed"), CA an, uong (potion/sua/mat
 * ong), giuong cung/dinh ba, len day no VA chan khien DEU lam giam toc do di
 * chuyen O CUNG MOT CO CHE vanilla (khong rieng gi khien). Chi kiem tra
 * isBlocking() nghia la 4/5 truong hop con lai (an, uong, cung, no) HOAN TOAN
 * KHONG duoc kiem tra - ai "insta-eat"/"NoSlow khi ban cung" ma khong giu
 * khien se lot qua check nay hoan toan.
 *
 * Sua: quay lai dung isHandRaised() (bao quat ca 5 truong hop), nhung LOAI TRU
 * rieng 2 truong hop da biet gay bao sai (ngu giuong, dang o thuyen) thay vi bo
 * han ca isHandRaised() - giu duoc do bao quat, van tranh duoc 2 bug da biet.
 */
public class NoSlowCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    // Moi phan tu: {timestamp (ms), quang duong (block) di duoc tu su kien truoc do}
    private final Map<UUID, Deque<double[]>> samples = new ConcurrentHashMap<>();

    public NoSlowCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double maxSlowedSpeed;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.noslow.enabled", true);
        maxSlowedSpeed = plugin.getConfig().getDouble("anticheat.checks.noslow.max-speed", 3.0);
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

        UUID uuid = player.getUniqueId();

        // Bao quat CA 5 truong hop vanilla lam giam toc do (an/uong/cung/no/khien),
        // tru 2 truong hop da biet isHandRaised() bao sai (ngu giuong, o thuyen).
        boolean shouldBeSlowed = player.isHandRaised() && !player.isSleeping()
                && !(player.getVehicle() instanceof org.bukkit.entity.Boat);
        if (!shouldBeSlowed) {
            samples.remove(uuid);
            return;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle() || player.isSwimming()) return;

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        long now = System.currentTimeMillis();
        Deque<double[]> deque = samples.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        synchronized (deque) {
            deque.addLast(new double[]{now, dist});
            long windowMs = 1000L;
            while (!deque.isEmpty() && now - deque.peekFirst()[0] > windowMs) {
                deque.pollFirst();
            }

            double accum = 0.0;
            for (double[] sample : deque) accum += sample[1];

            if (accum > maxSlowedSpeed) {
                plugin.flag(player, "noslow", "NoSlow (toc do " + String.format("%.1f", accum) + " block/s khi dang dung vat pham/khien)");
                deque.clear();
            }
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi samples khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }
}
