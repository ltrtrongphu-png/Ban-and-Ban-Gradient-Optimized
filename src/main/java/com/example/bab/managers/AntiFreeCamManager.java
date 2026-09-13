package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Y tuong lay tu 1 plugin tham khao: KHONG co gang "phat hien" FreeCam/ESP (da
 * giai thich nhieu lan trong qua trinh phat trien - ve nguyen tac khong the lam
 * duoc qua goi tin), ma thay vao do "PHA" gia tri cua no bang cach ap hieu ung
 * Blindness (mu tam thoi) THAT SU len nhan vat khi ho o do sau nhat dinh. Vi
 * Blindness la hieu ung che man hinh o tang render cua chinh client, camera
 * FreeCam (chi tach roi goc nhin, khong tat hieu ung man hinh) cung bi mu theo -
 * lam viec "do tham" tro nen vo dung ma khong can biet ai dang dung FreeCam.
 *
 * KHONG phat oan ai (chi la 1 debuff vanilla binh thuong, khong cham diem VL,
 * khong the dan toi ban).
 *
 * LUU Y QUAN TRONG - CAN CAN NHAC KY TRUOC KHI BAT: ap dung Blindness cho MOI
 * nguoi choi o do sau nhat dinh se anh huong ca nguoi choi BINH THUONG dang
 * dao ham/xay ham that (rat pho bien, khong lien quan gi toi raid). Vi vay:
 * - MAC DINH TAT (enabled: false) - chi bat neu ban thuc su can chong raid.
 * - Nguong do sau mac dinh rat thap (gan bedrock) de giam toi da anh huong toi
 *   hoat dong dao ham binh thuong o cac do cao pho bien hon.
 * - Nen thu nghiem ky va lang nghe phan hoi nguoi choi truoc khi bat rong rai.
 */
public class AntiFreeCamManager implements ConfigReloadable {

    private final BaB plugin;
    private BukkitTask task;

    private volatile int yThreshold;
    private volatile int durationTicks;

    public AntiFreeCamManager(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        yThreshold = ConfigUtil.getBoundedInt(plugin, "antifreecam.y-threshold", -58, -2032, 2032);
        durationTicks = ConfigUtil.getBoundedInt(plugin, "antifreecam.blindness-duration-seconds", 4, 1, 3600) * 20;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("antifreecam.enabled", false)) return;

        // SUGGESTION doc E: gia tri <= 0 truyen vao runTaskTimer se nem IllegalArgumentException
        // ngay khi bat tinh nang nay.
        int intervalTicks = ConfigUtil.getBoundedInt(plugin, "antifreecam.check-interval-seconds", 5, 1, 3600) * 20;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("[BaB] AntiFreeCam (mu tam thoi khi dao sau) da bat.");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isExempt(player)) continue;
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
            if (player.getLocation().getY() > yThreshold) continue;

            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0, false, false, false));
        }
    }
}
