package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kiem tra toc do di chuyen (Speed) va bay/lo lung bat thuong (Flight).
 *
 * NANG CAP (so voi ban truoc): ban cu dung 1 he so nhan (max-speed-multiplier)
 * co dinh, KHONG phan biet nguoi choi dang dung tren dat thuong hay tren
 * bang/soul sand - de bat duoc ca truong hop hop le tren bang (toc do truot
 * tren bang xanh co the len ~9 o/giay, cao hon nhieu toc do sprint binh thuong
 * 5.6 o/giay), he so phai de RAT RONG (1.35x = cho phep toi 35% vuot muc), dan
 * toi mot so hack toc do nhe (10-30%) lot qua duoc.
 *
 * O day, thay vi 1 he so co dinh cho MOI truong hop, ta MO HINH HOA rieng
 * ngu canh (loai khoi duoi chan: bang thuong/bang dong/bang xanh, soul sand,
 * bang boost tu Depth Strider khi boi...). Khi da biet ro ngu canh nao duoc
 * phep nhanh hon va nhanh hon BAO NHIEU, co the SIET he so mac dinh chung lai
 * (vd 1.15x thay vi 1.35x) ma KHONG tang bao sai - ket qua la bat duoc hack
 * toc do nhe hon truoc day se lot qua.
 *
 * Tuong tu voi Flight: thay vi dem "so tick lien tiep khong roi" (flat), ta
 * DU DOAN duong roi tu do theo cong thuc vat ly nguoi choi cua Minecraft (cong
 * khai, xem wiki.vg - KHONG lay tu 1 plugin cu the nao): moi tick, van toc doc
 * (Vy) giam ~0.08 (trong luc) roi nhan 0.98 (can khong khi): Vy' = (Vy - 0.08)
 * * 0.98. So sanh do lech Y THUC TE voi du doan; neu lech cao hon du doan LIEN
 * TUC nhieu tick (khong phai 1 lan don le - co the la lag/rubber-band), day la
 * dau hieu dang tin cay hon nhieu so voi chi dem "khong roi".
 *
 * TOI UU HIEU NANG: implement ConfigReloadable - moi gia tri config duoc doc
 * 1 lan (constructor) va cache vao field, thay vi doc lai getConfig() MOI LAN
 * PlayerMoveEvent xay ra (su kien day dac nhat trong Bukkit - co the hang chuc
 * lan/giay/nguoi choi). Goi loadConfigValues() lai khi /bab reload duoc chay.
 */
public class MovementCheck implements Listener, ConfigReloadable {

    private final BaB plugin;
    private final Map<UUID, Long> teleportGraceUntil = new HashMap<>();
    private final Map<UUID, Long> damageGraceUntil = new HashMap<>();
    private final Map<UUID, Double> lastVerticalVelocity = new HashMap<>();
    private final Map<UUID, Integer> verticalViolationStreak = new HashMap<>();
    private final Map<UUID, Integer> speedViolationStreak = new HashMap<>();

    // Sliding window (1s) cua quang duong ngang di chuyen - xem giai thich o
    // ban cu ve ly do dung Deque thay vi cua so co dinh (chong burst do lag).
    private final Map<UUID, Deque<double[]>> speedSamples = new HashMap<>();

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    // --- Gia tri config duoc cache (xem loadConfigValues()) ---
    private volatile boolean speedEnabled;
    private volatile boolean flightEnabled;
    private volatile double maxSpeedMultiplier;
    private volatile double verticalTolerance;
    private volatile int maxAirborneTicks;
    private volatile int graceTicksAfterTeleport;
    private volatile int graceTicksAfterDamage;

    public MovementCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    /** Doc lai toan bo gia tri config lien quan toi check nay tu config.yml. */
    @Override
    public void loadConfigValues() {
        speedEnabled = plugin.getConfig().getBoolean("anticheat.checks.speed.enabled", true);
        flightEnabled = plugin.getConfig().getBoolean("anticheat.checks.flight.enabled", true);
        maxSpeedMultiplier = plugin.getConfig().getDouble("anticheat.checks.speed.max-speed-multiplier", 1.15);
        verticalTolerance = plugin.getConfig().getDouble("anticheat.checks.flight.vertical-tolerance", 0.03);
        maxAirborneTicks = plugin.getConfig().getInt("anticheat.checks.flight.max-airborne-ticks", 6);
        graceTicksAfterTeleport = plugin.getConfig().getInt("anticheat.checks.speed.grace-ticks-after-teleport", 40);
        graceTicksAfterDamage = plugin.getConfig().getInt("anticheat.checks.speed.grace-ticks-after-damage", 20);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        teleportGraceUntil.put(uuid, System.currentTimeMillis() + graceTicksAfterTeleport * 50L);
        verticalViolationStreak.remove(uuid);
        lastVerticalVelocity.remove(uuid);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        damageGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + graceTicksAfterDamage * 50L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        checkSpeed(player, from, horizontalDistance);
        checkFlight(player, from, to);
    }

    private boolean inGrace(Player player) {
        long now = System.currentTimeMillis();
        Long tp = teleportGraceUntil.get(player.getUniqueId());
        if (tp != null && now < tp) return true;
        Long dmg = damageGraceUntil.get(player.getUniqueId());
        return dmg != null && now < dmg;
    }

    private void checkSpeed(Player player, Location from, double horizontalDistance) {
        if (!speedEnabled) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) return;
        if (player.isSwimming() || isInLiquid(player)) return; // toc do boi mo hinh phuc tap rieng, khong nam trong pham vi check nay
        if (inGrace(player)) return;

        double externalVelocity = player.getVelocity().setY(0).length();
        if (externalVelocity > 0.6) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<double[]> deque = speedSamples.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        deque.addLast(new double[]{now, horizontalDistance});
        long windowMs = 1000L;
        while (!deque.isEmpty() && now - deque.peekFirst()[0] > windowMs) {
            deque.pollFirst();
        }

        double accum = 0.0;
        for (double[] sample : deque) accum += sample[1];

        double maxAllowed = computeMaxSpeedPerSecond(player, from);
        if (accum > maxAllowed) {
            // FIX (giam bao sai khi da siet nguong): thay vi flag ngay lan dau vuot
            // nguong, doi it nhat 2 lan lien tiep (~trong cua so ngan) - loc bot
            // truong hop 1 goi tin bu don le sau giat mang lam tong tam thoi vuot nguong.
            int streak = speedViolationStreak.merge(uuid, 1, Integer::sum);
            if (streak >= 2) {
                plugin.flag(player, "speed", "Speed");
                deque.clear();
                speedViolationStreak.put(uuid, 0);
            }
        } else {
            speedViolationStreak.put(uuid, 0);
        }
    }

    private double computeMaxSpeedPerSecond(Player player, Location from) {
        double base = 5.62; // toc do sprint toi da xap xi tren dat bang (block/giay)
        double multiplier = 1.0;

        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            multiplier += 0.2 * (speedEffect.getAmplifier() + 1);
        }
        // FIX: Slowness lam giam toc do, truoc day khong duoc tru vao maxAllowed -
        // day la 1 phan cua viec mo hinh CHINH XAC HON, giup cac tham so khac (buffer)
        // co the siet chat hon ma khong so anh huong oan nguoi dang bi Slowness.
        PotionEffect slowEffect = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slowEffect != null) {
            multiplier -= 0.15 * (slowEffect.getAmplifier() + 1);
            multiplier = Math.max(multiplier, 0.05);
        }

        // Mo hinh theo loai khoi duoi chan - he so cong khai, xap xi tu vat ly di
        // chuyen Minecraft (khong lay tu 1 plugin cu the): bang cang "tron" cang
        // truot nhanh, soul sand/honey lam cham lai.
        Block ground = from.clone().subtract(0, 0.1, 0).getBlock();
        Material groundType = ground.getType();
        double groundFactor = switch (groundType) {
            case BLUE_ICE -> 1.65;
            case PACKED_ICE -> 1.45;
            case ICE, FROSTED_ICE -> 1.32;
            case SLIME_BLOCK -> 1.25; // truot/nay tren slime co the tao quang duong ngang lon bat thuong trong 1 tick
            case SOUL_SAND -> 0.6;
            case HONEY_BLOCK -> 0.55;
            default -> 1.0;
        };

        return base * multiplier * groundFactor * maxSpeedMultiplier;
    }

    private boolean isInLiquid(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.3, 0).getBlock().getType();
        return feet == Material.WATER || feet == Material.LAVA || below == Material.WATER || below == Material.LAVA;
    }

    private void checkFlight(Player player, Location from, Location to) {
        if (!flightEnabled) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying() || player.isGliding()) return;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return;
        if (player.isInsideVehicle()) return;
        if (player.isClimbing() || player.isSwimming() || isInLiquid(player)) return;
        if (inGrace(player)) return;

        UUID uuid = player.getUniqueId();
        double actualDeltaY = to.getY() - from.getY();

        if (player.isOnGround()) {
            lastVerticalVelocity.put(uuid, 0.0);
            verticalViolationStreak.put(uuid, 0);
            return;
        }

        Double previous = lastVerticalVelocity.get(uuid);
        if (previous == null) {
            // Chua co du lieu tick truoc (vd vua join/teleport) - luu lai va cho tick sau.
            lastVerticalVelocity.put(uuid, actualDeltaY);
            return;
        }

        double jumpBoostBonus = 0.0;
        PotionEffect jumpBoost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jumpBoost != null) jumpBoostBonus = 0.1 * (jumpBoost.getAmplifier() + 1);

        double predictedDeltaY;
        if (previous == 0.0 && actualDeltaY > 0) {
            // Tick bat dau nhay tu mat dat: van toc ban dau ~0.42 (+jump boost) la
            // HOP LE, khong the du doan tu Vy_truoc (= 0 luc con o mat dat). Chap
            // nhan tick nay, nhung VAN LUU LAI lam moc cho du doan cua CAC TICK SAU.
            predictedDeltaY = actualDeltaY;
        } else {
            predictedDeltaY = (previous - GRAVITY) * DRAG;
        }

        double tolerance = verticalTolerance + jumpBoostBonus;
        double deviation = actualDeltaY - predictedDeltaY;

        if (deviation > tolerance) {
            int streak = verticalViolationStreak.merge(uuid, 1, Integer::sum);
            if (streak >= maxAirborneTicks) {
                plugin.flag(player, "flight", "Flight (lech du doan roi: " + String.format("%.3f", deviation) + ")");
                verticalViolationStreak.put(uuid, 0);
            }
        } else {
            verticalViolationStreak.put(uuid, 0);
        }

        lastVerticalVelocity.put(uuid, actualDeltaY);
    }

    // FIX (memory leak): don entry cua nguoi choi khoi cac map tren khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        teleportGraceUntil.remove(uuid);
        damageGraceUntil.remove(uuid);
        speedSamples.remove(uuid);
        lastVerticalVelocity.remove(uuid);
        verticalViolationStreak.remove(uuid);
        speedViolationStreak.remove(uuid);
    }
}
