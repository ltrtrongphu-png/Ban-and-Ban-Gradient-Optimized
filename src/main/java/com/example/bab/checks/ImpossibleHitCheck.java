package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * Kiem tra ImpossibleHit (moi, tham khao tu 1 plugin khac) - phat hien danh
 * trung muc tieu trong khi goc nhin dang huong RA XA muc tieu (vd quay lung
 * lai). Client vanilla that khong the lam duoc dieu nay vi thao tac tan cong
 * luon gan lien voi huong camera. Nguong am rat thoai mai (-0.1, tuong duong
 * ~96 do lech) de tranh bao sai voi hitbox lon/mob dung sat nhau.
 */
public class ImpossibleHitCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;

    public ImpossibleHitCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile double maxNegativeDot;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.impossiblehit.enabled", true);
        maxNegativeDot = plugin.getConfig().getDouble("anticheat.checks.impossiblehit.max-negative-dot", -0.1);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (plugin.isExempt(player)) return;

        Vector toTarget = target.getLocation().add(0, target.getHeight() / 2.0, 0)
                .toVector().subtract(player.getEyeLocation().toVector()).normalize();
        Vector look = player.getEyeLocation().getDirection().normalize();
        double dot = look.dot(toTarget);

        if (dot < maxNegativeDot) {
            plugin.flag(player, "impossiblehit", "ImpossibleHit (danh trung khi nhin ra xa muc tieu)");
        }
    }
}
