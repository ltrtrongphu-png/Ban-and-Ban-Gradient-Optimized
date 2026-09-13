package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiem tra KillAura (danh trung qua xa - reach), AutoClicker (CPS + do deu cua
 * khoang cach click), va Multi-aura (danh trung nhieu muc tieu KHAC NHAU trong
 * 1 khoang thoi gian con nguoi khong the lam duoc, tru don quet - sweep).
 *
 * FIX (reach): tinh khoang cach toi diem GAN NHAT tren bounding box cua entity
 * thay vi 1 diem duy nhat (goc/chan) - chinh xac hon voi mob cao.
 *
 * NANG CAP (AutoClicker): ban cu CHI dung nguong CPS phang (vd max-cps: 22) -
 * ai giu CPS duoi nguong (vd macro dat 20 CPS) se lot qua hoan toan du click
 * DEU TOI MUC KHONG THE la con nguoi that (con nguoi luon co jitter/dao dong
 * nho giua cac lan click, ke ca "click nhanh nhat"). O day them 1 tin hieu
 * THONG KE thu 2: he so bien thien (CV) cua CAC KHOANG CACH GIUA 2 LAN CLICK
 * LIEN TIEP - neu CV qua thap (click qua deu ve THOI GIAN) VA CPS trung binh
 * du cao, flag rieng du CPS chua vuot nguong flat.
 *
 * NANG CAP (Multi-aura): danh trung 2 THUC THE KHAC NHAU trong khoang thoi
 * gian rat ngan (vai chuc mili giay) la dau hieu cua killaura "multi-target" -
 * loai tru truong hop don quet (sweep attack) vi day la co che vanilla hop le
 * co the gay sat thuong nhieu muc tieu CUNG LUC trong 1 lan vung kiem.
 *
 * TOI UU HIEU NANG: implement ConfigReloadable - cache toan bo gia tri config
 * (8 gia tri, doc tren MOI lan tan cong/vung tay truoc day) vao field ngay tu
 * constructor, chi doc lai getConfig() khi loadConfigValues() duoc goi (vd tu
 * /bab reload). onSwing chay tren PlayerAnimationEvent - gan nhu moi lan nguoi
 * choi click chuot - nen day la 1 trong nhung diem doc config nong nhat truoc day.
 */
public class KillAuraCheck implements Listener, ConfigReloadable {

    private final BaB plugin;
    private final Map<UUID, Deque<Long>> swingTimestamps = new ConcurrentHashMap<>();
    // {timestamp lan danh trung gan nhat, UUID muc tieu lan do} - dung cho multi-aura.
    private final Map<UUID, long[]> lastHitTimestamp = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastHitTarget = new ConcurrentHashMap<>();

    // --- Gia tri config duoc cache (xem loadConfigValues()) ---
    private volatile boolean killauraEnabled;
    private volatile double maxReach;
    private volatile boolean multiauraEnabled;
    private volatile long multiauraMinIntervalMs;
    private volatile boolean autoclickerEnabled;
    private volatile int autoclickerMaxCps;
    private volatile long autoclickerSampleWindowMs;
    private volatile boolean autoclickerTimingEnabled;
    private volatile int autoclickerTimingMinSamples;
    private volatile double autoclickerTimingMinCps;
    private volatile double autoclickerTimingMaxCv;

    public KillAuraCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    /** Doc lai toan bo gia tri config lien quan toi check nay tu config.yml. */
    @Override
    public void loadConfigValues() {
        killauraEnabled = plugin.getConfig().getBoolean("anticheat.checks.killaura.enabled", true);
        maxReach = plugin.getConfig().getDouble("anticheat.checks.killaura.max-reach", 4.2);
        multiauraEnabled = plugin.getConfig().getBoolean("anticheat.checks.multiaura.enabled", true);
        multiauraMinIntervalMs = plugin.getConfig().getLong("anticheat.checks.multiaura.min-interval-ms", 100);
        autoclickerEnabled = plugin.getConfig().getBoolean("anticheat.checks.autoclicker.enabled", true);
        autoclickerMaxCps = plugin.getConfig().getInt("anticheat.checks.autoclicker.max-cps", 22);
        autoclickerSampleWindowMs = plugin.getConfig().getLong("anticheat.checks.autoclicker.sample-window-ms", 1000);
        autoclickerTimingEnabled = plugin.getConfig().getBoolean("anticheat.checks.autoclicker-timing.enabled", true);
        autoclickerTimingMinSamples = plugin.getConfig().getInt("anticheat.checks.autoclicker-timing.min-samples", 10);
        autoclickerTimingMinCps = plugin.getConfig().getDouble("anticheat.checks.autoclicker-timing.min-cps", 9.0);
        autoclickerTimingMaxCv = plugin.getConfig().getDouble("anticheat.checks.autoclicker-timing.max-coefficient-of-variation", 0.08);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (plugin.isExempt(player)) return;

        checkReach(player, target);
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            checkMultiAura(player, target);
        }
    }

    private void checkReach(Player player, LivingEntity target) {
        if (!killauraEnabled) return;

        double distance = distanceToBoundingBox(player.getEyeLocation(), target);

        if (distance > maxReach) {
            plugin.flag(player, "killaura", "KillAura (Reach " + String.format("%.1f", distance) + ")");
        }
    }

    private void checkMultiAura(Player player, LivingEntity target) {
        if (!multiauraEnabled) return;

        UUID uuid = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        long now = System.currentTimeMillis();

        UUID previousTarget = lastHitTarget.put(uuid, targetId);
        long[] previousTime = lastHitTimestamp.put(uuid, new long[]{now});

        if (previousTarget == null || previousTime == null) return;
        if (previousTarget.equals(targetId)) return; // cung 1 muc tieu - danh lien tiep 1 mob la binh thuong

        long elapsed = now - previousTime[0];
        if (elapsed < multiauraMinIntervalMs) {
            plugin.flag(player, "multiaura", "Multi-aura (2 muc tieu khac nhau trong " + elapsed + "ms)");
        }
    }

    /** Khoang cach tu 1 diem (mat nguoi choi) toi diem GAN NHAT tren bounding box cua entity. */
    private double distanceToBoundingBox(Location eye, LivingEntity target) {
        BoundingBox box = target.getBoundingBox();

        double clampedX = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double clampedY = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double clampedZ = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));

        double dx = eye.getX() - clampedX;
        double dy = eye.getY() - clampedY;
        double dz = eye.getZ() - clampedZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = swingTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > autoclickerSampleWindowMs) {
                timestamps.pollFirst();
            }

            if (autoclickerEnabled) {
                if (timestamps.size() > autoclickerMaxCps) {
                    plugin.flag(player, "autoclicker", "AutoClicker (CPS: " + timestamps.size() + ")");
                    timestamps.clear();
                    return;
                }
            }

            checkClickTimingUniformity(player, timestamps);
        }
    }

    private void checkClickTimingUniformity(Player player, Deque<Long> timestamps) {
        if (!autoclickerTimingEnabled) return;

        if (timestamps.size() < autoclickerTimingMinSamples) return;

        Long[] arr = timestamps.toArray(new Long[0]);
        double[] intervals = new double[arr.length - 1];
        for (int i = 1; i < arr.length; i++) {
            intervals[i - 1] = arr[i] - arr[i - 1];
        }

        double meanCheck = 0;
        for (double v : intervals) meanCheck += v;
        if (meanCheck <= 0) return; // tranh StatsUtil nem loi khi trung binh <= 0 (2 click cung 1 ms)

        com.example.bab.util.StatsUtil.Result stats = com.example.bab.util.StatsUtil.coefficientOfVariation(intervals);
        double mean = stats.mean;
        double coefficientOfVariation = stats.coefficientOfVariation;

        double cps = 1000.0 / mean;
        if (cps < autoclickerTimingMinCps) return; // click cham - khong can xet do deu, con nguoi click cham van co the rat deu

        if (coefficientOfVariation <= autoclickerTimingMaxCv) {
            plugin.flag(player, "autoclicker-timing", "AutoClicker (click qua deu - CV: "
                    + String.format("%.3f", coefficientOfVariation) + ", ~" + String.format("%.1f", cps) + " CPS)");
            timestamps.clear();
        }
    }

    // FIX (memory leak): don entry cua nguoi choi khoi cac map tren khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        swingTimestamps.remove(uuid);
        lastHitTimestamp.remove(uuid);
        lastHitTarget.remove(uuid);
    }
}
