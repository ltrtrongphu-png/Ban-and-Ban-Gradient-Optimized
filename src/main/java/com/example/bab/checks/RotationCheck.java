package com.example.bab.checks;

import com.example.bab.BaB;
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
 * Kiem tra Rotation: (1) pitch vat ly khong hop le (giu nguyen tu ban truoc),
 * va (2) MOI - phat hien xoay camera "qua deu" (aim-assist/GCD) bang thong ke
 * PHUONG SAI, dung nhu ghi chu TODO da de lai trong ban truoc: "neu muon phat
 * hien aim-assist/GCD that su, can do phuong sai giua cac delta lien tiep de
 * tim 'qua deu' chu khong phai 'qua nhanh'".
 *
 * Y TUONG (ky thuat cong khai, dung trong nhieu anticheat thuc te - khong
 * sao chep tu 1 san pham cu the): camera cua con nguoi that, DU QUAY NHANH toi
 * dau, van co "rung" tu nhien (jitter) giua cac tick - do la ban chat vat ly
 * cua chuyen dong tay/chuot, khong co ai xoay voi TOC DO GOC HOAN TOAN DEU tren
 * nhieu tick lien tiep. Nguoc lai, aim-assist/silent-aim thuong noi suy (interpolate)
 * goc nhin theo 1 buoc gan nhu co dinh moi tick khi dang "khoa" muc tieu -> day
 * chinh la dau hieu "qua deu" (phuong sai RAT THAP) trong khi van quay NHANH
 * (khac voi nguoi choi dung yen/nhin cham cham von cung co phuong sai thap
 * nhung KHONG quay nhanh - can ca 2 dieu kien cung luc moi flag).
 *
 * De AN TOAN (giam bao sai toi da): chi tinh tren cac tick THUC SU co xoay
 * (bo qua delta ~0), yeu cau toi thieu 15 mau trong cua so 1.5 giay, VA yeu
 * cau dieu kien nay dung trong 2 cua so danh gia LIEN TIEP truoc khi flag that su.
 */
public class RotationCheck implements Listener, ConfigReloadable {

    private static final double MIN_SAMPLES = 15;
    private static final long WINDOW_MS = 1500;
    private static final double MIN_DELTA_TO_COUNT = 0.05; // do - bo qua "khong xoay" (nhieu lam tron float)

    private final BaB plugin;
    private final Map<UUID, Deque<double[]>> yawDeltaSamples = new ConcurrentHashMap<>(); // {timestamp, |deltaYaw|}
    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> uniformityStreak = new ConcurrentHashMap<>();

    public RotationCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean rotationEnabled;
    private volatile boolean rotationUniformityEnabled;
    private volatile double minAvgDegPerTick;
    private volatile double maxCv;

    @Override
    public void loadConfigValues() {
        rotationEnabled = plugin.getConfig().getBoolean("anticheat.checks.rotation.enabled", true);
        rotationUniformityEnabled = plugin.getConfig().getBoolean("anticheat.checks.rotation-uniformity.enabled", true);
        minAvgDegPerTick = plugin.getConfig().getDouble("anticheat.checks.rotation-uniformity.min-avg-deg-per-tick", 3.0);
        maxCv = plugin.getConfig().getDouble("anticheat.checks.rotation-uniformity.max-coefficient-of-variation", 0.15);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        Location to = event.getTo();
        if (to == null) return;

        checkInvalidPitch(player, to);
        checkRotationUniformity(player, to);
    }

    private void checkInvalidPitch(Player player, Location to) {
        if (!rotationEnabled) return;

        float pitch = to.getPitch();
        // Vat ly Minecraft: pitch hop le luon nam trong [-90, 90]. Vuot qua muc nay
        // (voi buffer nho de tranh loi lam tron float) la dau hieu KHONG THE co that
        // tu 1 client vanilla, gan nhu chac chan la goi tin bi can thiep.
        if (pitch > 90.5f || pitch < -90.5f) {
            plugin.flag(player, "rotation", "Rotation (pitch bat thuong: " + String.format("%.1f", pitch) + ")");
        }
    }

    private void checkRotationUniformity(Player player, Location to) {
        if (!rotationUniformityEnabled) return;

        UUID uuid = player.getUniqueId();
        float currentYaw = to.getYaw();
        Float previousYaw = lastYaw.put(uuid, currentYaw);
        if (previousYaw == null) return;

        double delta = Math.abs(normalizeAngle(currentYaw - previousYaw));
        if (delta < MIN_DELTA_TO_COUNT) return; // khong xoay dang ke tick nay - khong tinh vao mau

        long now = System.currentTimeMillis();
        Deque<double[]> samples = yawDeltaSamples.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        samples.addLast(new double[]{now, delta});
        while (!samples.isEmpty() && now - samples.peekFirst()[0] > WINDOW_MS) {
            samples.pollFirst();
        }

        if (samples.size() < MIN_SAMPLES) return;

        double[] values = new double[samples.size()];
        int i = 0;
        for (double[] s : samples) values[i++] = s[1];
        com.example.bab.util.StatsUtil.Result stats = com.example.bab.util.StatsUtil.coefficientOfVariation(values);
        double mean = stats.mean;
        double coefficientOfVariation = stats.coefficientOfVariation;

        boolean suspicious = mean >= minAvgDegPerTick && coefficientOfVariation <= maxCv;

        if (suspicious) {
            // Doi 2 cua so danh gia LIEN TIEP deu nghi ngo (~3 giay tong cong) truoc
            // khi flag - giam toi da nguy co bao sai voi nguoi choi co thao tac
            // xoay tay rat on dinh (hiem, nhung co the xay ra tu nhien trong thoi
            // gian ngan) ma khong phai dang dung aim-assist.
            int streak = uniformityStreak.merge(uuid, 1, Integer::sum);
            if (streak >= 2) {
                plugin.flag(player, "rotation-uniformity", "Rotation (xoay qua deu - CV: "
                        + String.format("%.3f", coefficientOfVariation) + ", TB: " + String.format("%.1f", mean) + " do/tick)");
                uniformityStreak.put(uuid, 0);
            }
            samples.clear();
        } else {
            uniformityStreak.put(uuid, 0);
        }
    }

    private double normalizeAngle(double angle) {
        angle = angle % 360.0;
        if (angle >= 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return angle;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        yawDeltaSamples.remove(uuid);
        lastYaw.remove(uuid);
        uniformityStreak.remove(uuid);
    }
}
