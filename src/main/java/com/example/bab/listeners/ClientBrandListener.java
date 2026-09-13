package com.example.bab.listeners;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;
import java.util.Locale;

/**
 * Doc kenh "minecraft:brand" ma client gui khi join, cho biet ten client (vd "vanilla",
 * "fabric", "forge"...). LUU Y QUAN TRONG: day CHI la thong tin tham khao, vi client hack
 * co the de dang gia mao gia tri nay thanh "vanilla". Vi vay chi canh bao cho staff,
 * TUYET DOI KHONG auto-ban dua vao check nay.
 */
public class ClientBrandListener implements Listener, PluginMessageListener, ConfigReloadable {

    private final BaB plugin;

    private volatile List<String> suspiciousBrands;
    private volatile String alertPermission;

    public ClientBrandListener(BaB plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "minecraft:brand", this);
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        suspiciousBrands = plugin.getConfig().getStringList("client-brand.suspicious-brands");
        alertPermission = plugin.getConfig().getString("settings.alert-permission", "bab.alerts");
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        // Giu lai hook nay de dam bao kenh duoc dang ky dung thoi diem tren mot so phien ban server.
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("minecraft:brand")) return;

        String brand;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            brand = in.readUTF();
        } catch (Exception ex) {
            return;
        }

        String lowerBrand = brand.toLowerCase(Locale.ROOT);

        for (String s : suspiciousBrands) {
            if (lowerBrand.contains(s.toLowerCase(Locale.ROOT))) {
                String msg = ChatColor.YELLOW + "[BaB] " + ChatColor.GRAY + "Canh bao (khong auto-ban): "
                        + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " bao client brand la '"
                        + ChatColor.RED + brand + ChatColor.GRAY + "'";
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission(alertPermission)) {
                        staff.sendMessage(msg);
                    }
                }
                plugin.getLogger().warning("[Client-Brand] " + player.getName() + " -> " + brand);
                break;
            }
        }
    }
}
