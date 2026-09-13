package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationManager {

    private final BaB plugin;
    private final Map<UUID, Map<String, Double>> violations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastViolationTime = new ConcurrentHashMap<>();

    public ViolationManager(BaB plugin) {
        this.plugin = plugin;
        startDecayTask();
    }

    public double addViolation(Player player, String check, double weight) {
        UUID uuid = player.getUniqueId();
        Map<String, Double> map = violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        double newVl = map.merge(check, weight, Double::sum);
        lastViolationTime.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(check, System.currentTimeMillis());
        return newVl;
    }

    public double getVl(Player player, String check) {
        Map<String, Double> map = violations.get(player.getUniqueId());
        return map == null ? 0 : map.getOrDefault(check, 0.0);
    }

    public void resetCheck(Player player, String check) {
        Map<String, Double> map = violations.get(player.getUniqueId());
        if (map != null) map.remove(check);
    }

    public void resetAll(Player player) {
        resetAll(player.getUniqueId());
    }

    // FIX (memory leak): trước đây chỉ có overload nhận Player, nên không thể gọi
    // gọn từ PlayerQuitEvent để dọn dữ liệu VL khi người chơi rời server -> map
    // violations/lastViolationTime phình to vô hạn theo số người chơi từng vào
    // server (không bao giờ được thu hồi). Thêm overload theo UUID để dùng được
    // trong listener onQuit chung của plugin.
    public void resetAll(UUID uuid) {
        violations.remove(uuid);
        lastViolationTime.remove(uuid);
    }

    private void startDecayTask() {
        long period = 20L; // moi giay 1 lan
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            // SUGGESTION doc E: decayAfterMs <= 0 lam VL "boc hoi" gan nhu ngay lap tuc
            // (vo hieu hoa anti-cheat), decayAmount am se LAM TANG VL thay vi giam theo
            // thoi gian (nguoc hoan toan y do cua tinh nang decay).
            long decayAfterMs = ConfigUtil.getBoundedLong(plugin, "anticheat.violation-decay-seconds", 60, 1L, 86_400L) * 1000L;
            double decayAmount = ConfigUtil.getBoundedDouble(plugin, "anticheat.violation-decay-amount", 5, 0.0, 100_000.0);

            for (Map.Entry<UUID, Map<String, Double>> entry : violations.entrySet()) {
                UUID uuid = entry.getKey();
                Map<String, Long> lastMap = lastViolationTime.get(uuid);
                for (Map.Entry<String, Double> checkEntry : entry.getValue().entrySet()) {
                    String check = checkEntry.getKey();
                    Long last = lastMap == null ? null : lastMap.get(check);
                    if (last == null || now - last >= decayAfterMs) {
                        double next = Math.max(0, checkEntry.getValue() - decayAmount);
                        checkEntry.setValue(next);
                    }
                }
            }
        }, period, period);
    }
}
