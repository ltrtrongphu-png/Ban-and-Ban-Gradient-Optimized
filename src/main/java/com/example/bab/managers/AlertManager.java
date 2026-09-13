package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class AlertManager implements ConfigReloadable {

    private final BaB plugin;

    private volatile String alertPermission;
    private volatile String alertFormat;
    private volatile String banBroadcastFormat;

    public AlertManager(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        alertPermission = plugin.getConfig().getString("settings.alert-permission", "bab.alerts");
        alertFormat = plugin.getConfig().getString("messages.alert-format",
                "&c[BaB] %player% - %check% VL %vl%/%threshold%");
        banBroadcastFormat = plugin.getConfig().getString("messages.ban-broadcast",
                "&4[BaB] %player% da bi ban - %reason%");
    }

    public void alertViolation(Player player, String check, double vl, double threshold) {
        String msg = ChatColor.translateAlternateColorCodes('&', alertFormat
                .replace("%player%", player.getName())
                .replace("%check%", check)
                .replace("%vl%", String.valueOf(Math.round(vl)))
                .replace("%threshold%", String.valueOf(Math.round(threshold))));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alertPermission)) {
                staff.sendMessage(msg);
            }
        }
        plugin.getLogger().info("[VL] " + player.getName() + " - " + check + " -> " + vl + "/" + threshold);
    }

    public void broadcastBan(Player player, String reason) {
        String msg = ChatColor.translateAlternateColorCodes('&', banBroadcastFormat
                .replace("%player%", player.getName())
                .replace("%reason%", reason));
        Bukkit.broadcastMessage(msg);
    }
}
