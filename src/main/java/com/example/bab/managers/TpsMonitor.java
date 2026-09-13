package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Theo doi TPS (tick/giay) cua server theo thoi gian thuc, dung trung binh
 * truot (EMA) de lam muot so lieu, tranh nhay lung tung theo tung tick le.
 * Lay y tuong tu plugin premium: khi server lag (TPS tut), moi check nen GIAM
 * do nhay de tranh bat oan hang loat nguoi choi cung luc do server khong theo
 * kip, khong phai do ho hack.
 */
public class TpsMonitor implements ConfigReloadable {

    private final BaB plugin;
    private long lastTickTime = System.currentTimeMillis();
    private double currentTps = 20.0;
    private BukkitTask task;

    private volatile boolean compensationEnabled;
    private volatile double minTps;
    private volatile double maxFactor;

    public TpsMonitor(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        compensationEnabled = plugin.getConfig().getBoolean("anticheat.tps-compensation.enabled", true);
        minTps = ConfigUtil.getBoundedDouble(plugin, "anticheat.tps-compensation.min-tps", 10.0, 0.1, 20.0);
        maxFactor = ConfigUtil.getBoundedDouble(plugin, "anticheat.tps-compensation.max-factor", 3.0, 1.0, 20.0);
    }

    public void start() {
        lastTickTime = System.currentTimeMillis();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long elapsed = now - lastTickTime;
            lastTickTime = now;
            if (elapsed <= 0) return;

            double instantTps = Math.min(20.0, 1000.0 / elapsed);
            // EMA: 90% gia tri cu + 10% gia tri moi - lam muot, tranh dao dong gia do 1 tick giat don le
            currentTps = currentTps * 0.9 + instantTps * 0.1;
        }, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public double getTps() {
        return currentTps;
    }

    /**
     * He so bu tru: 1.0 khi TPS day du (20), tang dan khi TPS tut xuong (toi da
     * gioi han o config de tranh vo hieu hoa hoan toan anticheat khi lag cuc nang).
     */
    public double getCompensationFactor() {
        if (!compensationEnabled) return 1.0;

        double tps = Math.max(minTps, Math.min(20.0, currentTps));
        double factor = 20.0 / tps;
        return Math.min(maxFactor, factor);
    }
}
