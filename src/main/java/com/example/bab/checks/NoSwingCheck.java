package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX (lan 2 - thay the toan bo huong tiep can cu):
 *
 * Ban truoc gia dinh SWEEP ATTACK sinh nhieu EntityDamageByEntityEvent voi
 * cause ENTITY_ATTACK tu 1 lan vung, nen dung 1 "cua so dedup" (lastAttackProcessed
 * + sweep-window-ms) de bo qua cac lan trung "an theo". Gia dinh nay SAI: Bukkit/
 * Paper da tach rieng nguyen nhan sat thuong cho don quet lan thanh
 * DamageCause.ENTITY_SWEEP_ATTACK (khac voi ENTITY_ATTACK cua muc tieu chinh).
 * Dong "if (event.getCause() != ENTITY_ATTACK) return;" da tu loai bo sweep tu
 * truoc khi cham toi logic dedup - nen co che dedup do KHONG giai quyet van de
 * thuc su, ma con tao lo hong: 2 don tan cong ENTITY_ATTACK that su, doc lap,
 * xay ra sat nhau (vd nguoi choi bam nhanh 2 muc tieu khac nhau) se "vay" do
 * tuoi cua nhau, cho phep mot chuoi tan cong khong-vung-tay lot qua sau lan
 * dau tien bi flag.
 *
 * NGUYEN NHAN THUC SU cua bao dong gia: code cu doi hoi swing phai den TRUOC
 * attack, trong 1 chieu thoi gian co dinh (250ms). Nhung thu tu goi tin swing
 * (PlayerAnimationEvent) va attack (EntityDamageByEntityEvent) do CLIENT gui
 * KHONG duoc dam bao co dinh - do do tre mang dao dong hoac lech tick xu ly o
 * server, goi attack co the duoc xu ly TRUOC goi swing tuong ung cua CHINH cu
 * click do, ngay ca voi nguoi choi bam nhip do binh thuong. Luc do lastSwing
 * dang giu gia tri CU (hoac null) -> bao oan.
 *
 * Sua: (1) bo han co che dedup sweep (khong can thiet, sweep da tu tach cause),
 * don gian hoa lai chi con 1 map lastSwing; (2) KHONG ket luan ngay tai thoi
 * diem attack - trai hoan phan quyet vai tick (verdict-delay-ticks), roi kiem
 * tra swing co xay ra trong cua so DOI XUNG quanh thoi diem attack hay khong
 * (ca truoc LAN sau), thay vi chi 1 chieu truoc do. Cach nay dung dung khuyen
 * nghi pho bien trong cong dong phat trien anti-cheat cho loai kiem tra dua
 * tren thu tu goi tin khong dam bao.
 */
public class NoSwingCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, Long> lastSwing = new ConcurrentHashMap<>();

    public NoSwingCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile long maxGapMs;
    private volatile long verdictDelayTicks;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.noswing.enabled", true);
        maxGapMs = plugin.getConfig().getLong("anticheat.checks.noswing.max-gap-ms", 250);
        verdictDelayTicks = plugin.getConfig().getLong("anticheat.checks.noswing.verdict-delay-ticks", 3L);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        lastSwing.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        // Don quet lan (sweep) da co DamageCause rieng (ENTITY_SWEEP_ATTACK),
        // khac ENTITY_ATTACK cua muc tieu chinh - nen bi loai o day mot cach
        // TU NHIEN, khong can dedup thu cong nhu ban truoc.
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();
        long attackTime = System.currentTimeMillis();

        // FIX: trai hoan ket luan vai tick de goi swing "den muon" (do thu tu
        // goi tin/tick khong dam bao) van kip duoc ghi nhan truoc khi cham diem.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Long swingTime = lastSwing.get(uuid);
            // Cua so DOI XUNG: chap nhan swing xay ra TRUOC HOAC SAU attack,
            // mien la trong khoang maxGapMs - khong con doi hoi thu tu co dinh.
            boolean hasFreshSwing = swingTime != null && Math.abs(attackTime - swingTime) <= maxGapMs;

            if (!hasFreshSwing) {
                plugin.flag(player, "noswing", "NoSwing (tan cong khong co animation vung tay)");
            }
        }, verdictDelayTicks);
    }

    // FIX (memory leak): don entry cua nguoi choi khoi lastSwing khi thoat server.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSwing.remove(event.getPlayer().getUniqueId());
    }
}
