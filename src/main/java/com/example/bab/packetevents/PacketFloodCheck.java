package com.example.bab.packetevents;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.checks.Toggleable;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Check MOI, dua tren PacketEvents (khong phai ProtocolLib) - dem so goi tin
 * Play.Client CHUYEN DONG (POSITION/POSITION_LOOK/ROTATION) nhan duoc trong 1
 * giay cho tung nguoi choi. Client vanilla that gui toi da ~20 goi/giay (1 goi
 * moi tick). Mot so ky thuat gian lan (packet injection tho so, mot vai loai
 * "no-fall"/"blink" client, hoac exploit gay lag/crash server bang flood goi
 * tin) gui vuot xa con so nay.
 *
 * Ly do dung PacketEvents thay vi them 1 ProtocolAdapter khac cua ProtocolLib
 * cho viec nay: PacketEvents dem goi tin o tang thap hon (truoc khi Bukkit gom
 * nhom/xu ly), nen chinh xac hon cho MUC DICH DEM TOC DO GOI TIN THO - day la
 * loai check ma ProtocolLib (voi cach xu ly bat dong bo qua Bukkit scheduler)
 * de bi sai lech hon.
 *
 * Day la check THONG KE (co the co bao sai voi mang rat khong on dinh/loi that
 * o tang mang), nen mac dinh nguong cho phep khoan dung rong (xem config
 * "anticheat.checks.packetflood").
 */
public class PacketFloodCheck extends PacketListenerAbstract implements ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<UUID, AtomicInteger> countPerSecond = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int maxPerSecond;

    public PacketFloodCheck(BaB plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.packetflood.enabled", true);
        maxPerSecond = plugin.getConfig().getInt("anticheat.checks.packetflood.max-packets-per-second", 40);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Goi 1 lan trong onEnable(), sau khi PacketEventsBridge.init() da chay. */
    public void register() {
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(this);
        // Reset bo dem moi giay + kiem tra nguong (chay tren main thread cua Bukkit
        // qua scheduler, khong phai thread cua PacketEvents, de goi plugin.flag() an toan).
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::evaluateAndReset, 20L, 20L);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isMovementPacket(event.getPacketType())) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (plugin.isExempt(player)) return;
        if (!enabled) return;

        countPerSecond.computeIfAbsent(player.getUniqueId(), k -> new AtomicInteger()).incrementAndGet();
    }

    private boolean isMovementPacket(com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type) {
        return type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                || type == PacketType.Play.Client.PLAYER_ROTATION
                || type == PacketType.Play.Client.PLAYER_FLYING;
    }

    private void evaluateAndReset() {
        for (Map.Entry<UUID, AtomicInteger> entry : countPerSecond.entrySet()) {
            int count = entry.getValue().getAndSet(0);
            if (count <= maxPerSecond) continue;

            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player == null || plugin.isExempt(player)) continue;

            plugin.flag(player, "packetflood", "PacketFlood (" + count + " goi chuyen dong/giay)");
        }
    }

    public void unregister() {
        countPerSecond.clear();
    }
}
