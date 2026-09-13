package com.example.bab.hackcheck;

import com.example.bab.BaB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cau noi voi plugin CheckHacks (chay doc lap, KHONG PHAI do BaB viet/quet) - thay
 * vi BaB tu quet lai bang ky thuat sign-packet (da gap loi khong on dinh qua nhieu
 * lan sua), BaB "lang nghe" ket qua CheckHacks in ra CONSOLE LOG, roi tu dong ap
 * dung he thong kick lan 1 / ban lan 2 (giong het luc BaB tu quet) len ket qua do.
 *
 * CACH NAY AN TOAN HON NHIEU: chi doc du lieu (khong gui goi tin, khong mo GUI,
 * khong dung packet nao ca) - ve nguyen tac khong the gay crash/kick nham nguoi
 * choi nhu ky thuat cu.
 *
 * YEU CAU: server phai co plugin CheckHacks (hoac plugin tuong tu in log cung
 * dinh dang) dang chay. Dinh dang log ky vong (dung theo dung nhung gi da thay
 * trong log thuc te cua ban):
 *   [CheckHacks] Check complete for <player>:
 *   [CheckHacks]   <ModName>: DETECTED
 *   [CheckHacks]   <ModName>: NOT_DETECTED
 * Neu ban dang dung phien ban CheckHacks khac in log khac dinh dang nay, sua lai
 * 2 Pattern ben duoi cho khop.
 */
public class CheckHacksBridge extends AbstractAppender {

    private static final Pattern PATTERN_HEADER = Pattern.compile("^\\[CheckHacks] Check complete for (.+):$");
    private static final Pattern PATTERN_LINE = Pattern.compile("^\\[CheckHacks]\\s+(.+): (DETECTED|NOT_DETECTED)$");

    private final BaB plugin;
    private String currentPlayerName;
    private final List<String> currentDetected = new ArrayList<>();
    private long lastLineAtMs;
    private org.apache.logging.log4j.core.Logger attachedLogger;

    public CheckHacksBridge(BaB plugin) {
        super("BaB-CheckHacksBridge", null, PatternLayout.createDefaultLayout(), false, null);
        this.plugin = plugin;
    }

    public void register() {
        if (!plugin.getConfig().getBoolean("checkhacks-bridge.enabled", false)) return;

        start();
        attachedLogger = (Logger) LogManager.getRootLogger();
        attachedLogger.addAppender(this);

        // Kiem tra dinh ky (moi 2 giay) xem 1 "phien" thu thap ket qua cho 1 nguoi
        // choi da ket thuc chua (khong co dong log moi trong 3 giay -> coi nhu xong).
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkFlush, 40L, 40L);

        plugin.getLogger().info("[BaB] Da ket noi voi log cua CheckHacks (cau noi console, an toan - chi doc du lieu).");
    }

    public void unregister() {
        if (attachedLogger != null) {
            attachedLogger.removeAppender(this);
        }
        stop();
    }

    @Override
    public synchronized void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        if (message == null || !message.startsWith("[CheckHacks]")) return;

        Matcher headerMatcher = PATTERN_HEADER.matcher(message);
        if (headerMatcher.matches()) {
            flushIfNeeded(); // Ket qua nguoi choi truoc (neu co) coi nhu da day du, xu ly ngay
            currentPlayerName = headerMatcher.group(1).trim();
            currentDetected.clear();
            lastLineAtMs = System.currentTimeMillis();
            return;
        }

        Matcher lineMatcher = PATTERN_LINE.matcher(message);
        if (lineMatcher.matches() && currentPlayerName != null) {
            String modName = lineMatcher.group(1).trim();
            String status = lineMatcher.group(2);
            if ("DETECTED".equals(status)) {
                currentDetected.add(modName);
            }
            lastLineAtMs = System.currentTimeMillis();
        }
    }

    /** Chay tren main thread dinh ky - neu qua 3 giay khong co dong log moi, coi nhu ket qua da day du. */
    private synchronized void checkFlush() {
        if (currentPlayerName == null) return;
        if (System.currentTimeMillis() - lastLineAtMs < 3000) return;
        flushIfNeeded();
    }

    private synchronized void flushIfNeeded() {
        if (currentPlayerName == null || currentDetected.isEmpty()) {
            currentPlayerName = null;
            currentDetected.clear();
            return;
        }

        String playerName = currentPlayerName;
        List<String> detected = new ArrayList<>(currentDetected);
        currentPlayerName = null;
        currentDetected.clear();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                plugin.getHackDetectionManager().handleExternalDetection(player, detected);
            }
        });
    }
}
