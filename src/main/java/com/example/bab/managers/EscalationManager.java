package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * He thong phat leo thang tuy chon (lay y tuong tu plugin premium tham khao):
 * thay vi chi co 2 muc "chua du nguong" / "du nguong thi ban thang" nhu truoc,
 * co the khai bao 1 chuoi hanh dong leo thang cho tung check, vi du:
 *   escalation: 'notify,kick,tempban:1h,ban'
 * Lan dau du nguong -> notify (chi canh bao, khong lam gi them - alert da tu
 * dong gui roi). Lan 2 -> kick. Lan 3 -> tempban 1 gio. Lan 4 tro di -> ban han.
 *
 * TUONG THICH NGUOC HOAN TOAN: neu 1 check KHONG khai bao "escalation" trong
 * config.yml, hanh vi giu nguyen y het truoc day (ban thang ngay khi du nguong,
 * reset toan bo VL). Chi check nao ban CHU DONG them dong "escalation:" moi
 * dung he thong nay.
 */
public class EscalationManager implements ConfigReloadable {

    private final BaB plugin;
    // UUID -> (checkId -> so lan da du nguong tu truoc toi nay)
    private final Map<UUID, Map<String, Integer>> levels = new ConcurrentHashMap<>();

    private volatile String kickMessageTemplate;
    private volatile String defaultBanDuration;

    public EscalationManager(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        kickMessageTemplate = plugin.getConfig().getString("anticheat.escalation-messages.kick",
                "&cBan bi kick do nghi ngo su dung hack (%check%)");
        defaultBanDuration = plugin.getConfig().getString("settings.ban-duration", "30d");
    }

    /** Goi khi 1 check da du nguong VL VA co khai bao "escalation" trong config. */
    public void escalate(Player player, String checkId, String displayName, String actionsConfig) {
        String[] actions = actionsConfig.split(",");
        if (actions.length == 0) return;

        UUID uuid = player.getUniqueId();
        Map<String, Integer> perCheck = levels.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int level = perCheck.merge(checkId, 1, Integer::sum);

        // Qua het danh sach hanh dong -> giu nguyen o hanh dong cuoi cung (thuong la "ban")
        int index = Math.min(level - 1, actions.length - 1);
        String action = actions[index].trim();

        plugin.getLogger().info("[BaB] Escalation " + player.getName() + " (" + checkId + ") lan " + level + " -> " + action);
        applyAction(player, displayName, action);
    }

    private void applyAction(Player player, String displayName, String action) {
        String[] parts = action.split(":", 2);
        String type = parts[0].trim().toLowerCase();
        String param = parts.length > 1 ? parts[1].trim() : null;

        switch (type) {
            case "notify":
                // Khong lam gi them - AlertManager da gui canh bao cho staff truoc do roi
                // (buoc nay chi ton tai de danh 1 "nac thang" trong chuoi escalation).
                break;

            case "kick": {
                String msg = ChatColor.translateAlternateColorCodes('&', kickMessageTemplate.replace("%check%", displayName));
                player.kickPlayer(msg);
                break;
            }

            case "tempban": {
                String duration = param != null ? param : "1h";
                plugin.getBanExecutor().banDirect(player, "AntiCheat (tam thoi): " + displayName, duration);
                break;
            }

            case "ban": {
                plugin.getBanExecutor().banDirect(player, "AntiCheat: " + displayName + " (tai pham nhieu lan)", defaultBanDuration);
                break;
            }

            default:
                plugin.getLogger().warning("[BaB] Hanh dong khong hop le trong escalation config: '" + action
                        + "' - cac hanh dong hop le: notify, kick, tempban:<thoi han>, ban");
        }
    }

    public void resetPlayer(UUID uuid) {
        levels.remove(uuid);
    }
}
