package com.example.bab.replay;

import com.example.bab.BaB;
import com.example.bab.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * "May ghi hinh" thay the video that: lien tuc ghi mot rolling buffer (vi du 20
 * giay gan nhat) vi tri + goc nhin cua moi nguoi choi dang online (chi luu tam
 * trong RAM, ton it tai nguyen). Khi mot nguoi bi flag (vi pham check nao do),
 * buffer hien tai duoc "chup" lai va GHI VAO DATABASE (SQLite, nen GZIP) de ton
 * tai vinh vien qua moi lan restart server - staff xem lai bat cu luc nao qua
 * lenh /bab replay.
 *
 * LUU Y QUAN TRONG: day KHONG PHAI video that (khong co pixel/hinh anh), chi la
 * du lieu vi tri+goc nhin. The gioi xung quanh luc phat lai la HIEN TAI, khong
 * phai qua khu - neu block da thay doi, canh vat luc replay se khac luc that.
 *
 * De tranh database phinh to vo han theo thoi gian, co 2 lop gioi han:
 * 1) Moi nguoi choi chi giu toi da N doan ghi gan nhat (tu dong xoa doan cu khi
 *    them doan moi, xu ly ngay trong DatabaseManager).
 * 2) Tac vu don dep dinh ky xoa het doan ghi qua han luu tru (retention-days).
 */
public class ReplayRecorder {

    private final BaB plugin;
    private final Map<UUID, Deque<ReplaySnapshot>> buffers = new ConcurrentHashMap<>();
    private BukkitTask recordingTask;
    private BukkitTask cleanupTask;
    private int maxBufferSize;

    public ReplayRecorder(BaB plugin) {
        this.plugin = plugin;
    }

    // Doc D (da kiem tra ky, khong phai bug): buffer moi nguoi choi DA duoc gioi han
    // boi maxBufferSize (trim trong recordSnapshot() moi lan ghi) VA duoc giai phong
    // hoan toan trong onQuit(). Khong co tang truong bo nho khong gioi han nhu doc
    // nghi ngo luc chi thay bytecode - da xac nhan bang code nguon that.
    public void start() {
        if (!plugin.getConfig().getBoolean("replay.enabled", true)) return;

        // SUGGESTION doc E: intervalTicks <= 0 se gay chia cho 0 khi tinh maxBufferSize
        // va truyen gia tri <= 0 cho runTaskTimer (Bukkit se nem IllegalArgumentException).
        int intervalTicks = ConfigUtil.getBoundedInt(plugin, "replay.sample-interval-ticks", 4, 1, 1200);
        int bufferSeconds = ConfigUtil.getBoundedInt(plugin, "replay.buffer-seconds", 20, 1, 3600);
        this.maxBufferSize = Math.max(1, (bufferSeconds * 20) / intervalTicks);

        recordingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.isExempt(player)) continue; // Khong ghi staff duoc mien kiem tra
                recordSnapshot(player);
            }
        }, intervalTicks, intervalTicks);

        // Don dep dinh ky cac doan ghi qua han luu tru trong database (mac dinh 6 tieng/lan)
        long cleanupPeriodTicks = 20L * 60L * 60L * 6L;
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // SUGGESTION doc E: gia tri <= 0 se xoa het moi replay clip vua luu (cutoff = hien tai
            // hoac tuong lai), pha huy toan bo lich su bang chung ma khong ai chu dinh.
            int retentionDays = ConfigUtil.getBoundedInt(plugin, "replay.retention-days", 14, 1, 3650);
            long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
            plugin.getDatabaseManager().purgeOldReplayClipsAsync(cutoff);
        }, 20L * 60L, cleanupPeriodTicks);
    }

    public void stop() {
        if (recordingTask != null) recordingTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
    }

    private void recordSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
        Deque<ReplaySnapshot> buffer = buffers.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        buffer.addLast(new ReplaySnapshot(System.currentTimeMillis(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        while (buffer.size() > maxBufferSize) {
            buffer.pollFirst();
        }
    }

    /** Goi khi mot nguoi choi bi flag - "chup" lai buffer hien tai va luu vao database. */
    public void saveClip(Player player, String checkId) {
        if (!plugin.getConfig().getBoolean("replay.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        Deque<ReplaySnapshot> buffer = buffers.get(uuid);
        if (buffer == null || buffer.isEmpty()) return;

        List<ReplaySnapshot> copy = new ArrayList<>(buffer);
        // SUGGESTION doc E: gia tri <= 0 truyen xuong SQL "LIMIT 0/-1" co the xoa het
        // toan bo clip cua nguoi choi ngay sau khi vua luu (xem pruneSql trong DatabaseManager).
        int maxClips = ConfigUtil.getBoundedInt(plugin, "replay.max-clips-per-player", 10, 1, 10_000);

        plugin.getDatabaseManager().saveReplayClipAsync(uuid, player.getName(), checkId,
                player.getWorld().getName(), System.currentTimeMillis(), copy, maxClips);
    }

    /** Doc danh sach doan ghi cua 1 nguoi choi tu database (bat dong bo, tra ve qua callback tren main thread). */
    public void getClipsAsync(UUID uuid, Consumer<List<ReplayClip>> callback) {
        plugin.getDatabaseManager().getReplayClipsAsync(uuid, callback);
    }

    /** Tim UUID theo ten nguoi choi - dung cache cua Bukkit (an toan/khong bi treo neu nguoi do tung vao server). */
    public UUID findUuidByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline != null ? offline.getUniqueId() : null;
    }

    public void onQuit(UUID uuid) {
        buffers.remove(uuid); // Giai phong buffer dang ghi trong RAM (cac clip da luu trong DB khong bi anh huong)
    }
}
