package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * FIX: ban goc khong xet truong hop nguoi choi bi danh trong GOC TUONG/GOC NHA -
 * day la tinh huong PVP RAT PHO BIEN, khi do vat ly game khong the day nguoi choi
 * di dau ca (bi chan boi block xung quanh), du KHONG he dung NoKnockback hack.
 * Hau qua: danh nhau trong goc/hanh lang hep de bi flag oan.
 *
 * Sua: kiem tra xem huong day (tu nguoi danh toi nguoi bi danh) co bi chan boi
 * block dac ngay ke ben khong - neu co, bo qua lan nay (khong the ket luan gi).
 * Ngoai ra keo dai delay-ticks mac dinh de giam nhieu do jitter mang.
 *
 * FIX (lan 2): ban do-vi-tri-sau-6-tick cu KHONG phan biet duoc "bi day boi
 * knockback" voi "tu nguoi choi bam W di ve phia doi thu" - hanh vi PVP hoan
 * toan binh thuong (gan nhu ai cung lam khi danh nhau) co the trung hoa gan
 * het khoang dich chuyen do knockback tao ra trong 6 tick, khien nguoi choi
 * HOP LE bi bao oan NoKnockback. Day la nguyen nhan bao dong gia rat pho bien
 * cho loai check do theo VI TRI thay vi do theo VAN TOC.
 *
 * Sua: chuyen sang do VAN TOC ngang cua nguoi choi 1 TICK SAU khi trung don
 * (thay vi vi tri sau 6 tick). Server ap luc knockback bang cach set velocity
 * ngay trong luc xu ly EntityDamageByEntityEvent (truoc khi handler nay chay
 * o priority mac dinh), nen doc velocity ngay sau do phan anh dung luc day
 * ma server da tao ra, TRUOC KHI input WASD cua nguoi choi kip tich luy du de
 * lam sai lech ket qua (1 tick = 50ms, qua ngan de input nguoi choi doi huong
 * dang ke van toc ma server vua ap). Van giu delay 1 tick (khong doc ngay lap
 * tuc trong cung tick) de dam bao velocity da duoc server cap nhat xong.
 *
 * LUU Y: day la thay doi kien truc (tu do vi tri sang do van toc), nen duoc
 * test ky tren server that truoc khi dung san xuat - khong the mo phong day
 * du vat ly va thu tu xu ly goi tin cua Bukkit trong moi truong khong co
 * server that.
 */
public class KnockbackCheck implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;

    public KnockbackCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    private volatile boolean enabled;
    private volatile int delayTicks;
    private volatile double minVelocity;

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.knockback.enabled", true);
        delayTicks = plugin.getConfig().getInt("anticheat.checks.knockback.velocity-check-delay-ticks", 1);
        minVelocity = plugin.getConfig().getDouble("anticheat.checks.knockback.min-velocity", 0.03);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (plugin.isExempt(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isInsideVehicle() || player.isGliding()) return;
        if (player.isBlocking()) return;

        // Neu ngay ben canh (huong ma knockback se day toi) da bi chan boi block dac,
        // vat ly khong the day nguoi choi di duoc - bo qua, khong the ket luan gi tu lan nay.
        if (isBlockedDirection(player, damager)) return;


        plugin.getServer().getScheduler().runTaskLater((Plugin) plugin, () -> {
            if (!player.isOnline()) return;

            Vector velocity = player.getVelocity();
            double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());

            if (horizontalSpeed < minVelocity) {
                plugin.flag(player, "knockback", "NoKnockback (van toc ngang chi " + String.format("%.3f", horizontalSpeed) + ")");
            }
        }, delayTicks);
    }

    private boolean isBlockedDirection(Player victim, LivingEntity damager) {
        Vector direction = victim.getLocation().toVector().subtract(damager.getLocation().toVector());
        if (direction.lengthSquared() == 0) return false;
        direction.normalize();

        Block relative = victim.getLocation().add(direction.getX(), 0, direction.getZ()).getBlock();
        return relative.getType().isSolid();
    }
}
