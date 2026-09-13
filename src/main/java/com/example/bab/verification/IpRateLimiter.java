package com.example.bab.verification;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.checks.Toggleable;
import com.example.bab.util.ConfigUtil;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chan ket noi don dap tu cung mot dia chi IP (dau hieu bot flood/DDoS ket noi).
 * Chay o AsyncPlayerPreLoginEvent - tuc la TRUOC KHI player object duoc tao, nen
 * khong anh huong toi nguoi choi that ket noi binh thuong mot lan.
 */
public class IpRateLimiter implements Listener, ConfigReloadable, Toggleable {

    private final BaB plugin;
    private final Map<String, Deque<Long>> joinTimestamps = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int maxJoinsPerMinute;
    private volatile String kickMessage;

    public IpRateLimiter(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("verification.enabled", true);
        maxJoinsPerMinute = ConfigUtil.getBoundedInt(plugin, "verification.max-joins-per-ip-per-minute", 6, 1, 10_000);
        kickMessage = plugin.getConfig().getString("verification.messages.kick-suspected-bot",
                "&cKet noi bi tu choi (nghi ngo bot).");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) return;

        // Doc D (da kiem tra ky): map ngoai la ConcurrentHashMap va Deque cua tung IP
        // duoc doc/ghi trong cung 1 khoi synchronized(timestamps) ben duoi -> thread-safe
        // du AsyncPlayerPreLoginEvent co the chay dong thoi tren nhieu thread ket noi khac
        // nhau. Khong phat hien race condition thuc te o day.
        String ip = event.getAddress().getHostAddress();
        long windowMs = 60_000L;
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = joinTimestamps.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxJoinsPerMinute) {
                String msg = ChatColor.translateAlternateColorCodes('&', kickMessage);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg);
                return;
            }
            timestamps.addLast(now);
        }
    }
}
