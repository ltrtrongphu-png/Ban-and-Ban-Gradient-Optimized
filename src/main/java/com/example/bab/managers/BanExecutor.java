package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BanExecutor implements ConfigReloadable {

    private final BaB plugin;

    private volatile boolean requirePlugin;
    private volatile String banPluginName;
    private volatile String banCommandTemplate;
    private volatile String defaultBanDuration;
    private volatile String banReasonTemplate;

    public BanExecutor(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        requirePlugin = plugin.getConfig().getBoolean("settings.require-ban-plugin", true);
        banPluginName = plugin.getConfig().getString("settings.ban-plugin-name", "LiteBans");
        banCommandTemplate = plugin.getConfig().getString("settings.ban-command", "ban %player% %duration% %reason%");
        defaultBanDuration = plugin.getConfig().getString("settings.ban-duration", "30d");
        banReasonTemplate = plugin.getConfig().getString("anticheat.ban-reason",
                "AntiCheat: nghi ngo su dung hack (%check%, VL: %vl%)");
    }

    /** Ban voi ly do dung san (dung cho AntiSpam hoac cac truong hop khong theo he thong VL). */
    public void banDirect(Player player, String reason, String duration) {
        if (requirePlugin && plugin.getServer().getPluginManager().getPlugin(banPluginName) == null) {
            plugin.getLogger().severe("[BaB] Khong tim thay plugin '" + banPluginName + "'! Khong the ban " + player.getName()
                    + ". Ly do du dinh: " + reason
                    + " | Neu ban dung plugin ban khac, doi 'settings.ban-plugin-name' trong config.yml cho dung ten,"
                    + " hoac dat 'settings.require-ban-plugin: false' de bo qua kiem tra nay.");
            return;
        }

        String command = banCommandTemplate
                .replace("%player%", player.getName())
                .replace("%duration%", duration)
                .replace("%reason%", reason);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        plugin.getLogger().warning("[BAN] " + player.getName() + " bi ban " + duration + " - ly do: " + reason);
        plugin.getAlertManager().broadcastBan(player, reason);
        plugin.getDatabaseManager().logBanAsync(player.getUniqueId(), player.getName(), reason, duration);
    }

    /** Ban theo he thong VL cua AntiCheat (tu dong dien ten check + VL vao ly do). */
    public void ban(Player player, String checkName, double vl) {
        String reason = banReasonTemplate
                .replace("%check%", checkName)
                .replace("%vl%", String.valueOf(Math.round(vl)));

        banDirect(player, reason, defaultBanDuration);
    }
}
