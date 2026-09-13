package com.example.bab.managers;

import com.example.bab.BaB;
import com.example.bab.replay.ReplayClip;
import com.example.bab.replay.ReplaySnapshot;
import org.bukkit.Bukkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Luu lich su vi pham va ban vao file SQLite (plugins/BaB/bab.db) de khong bi mat
 * du lieu khi restart server (khac voi ViolationManager chi luu tam trong RAM).
 * Moi thao tac ghi deu chay bat dong bo (runTaskAsynchronously) de khong lam
 * giat lag main thread cua server.
 */
public class DatabaseManager {

    private final BaB plugin;
    private Connection connection;

    /**
     * FIX (doc D "disconnect() khong doi tac vu bat dong bo"): moi tac vu ghi
     * (logViolationAsync, saveVerifiedUuidAsync, logBanAsync, saveReplayClipAsync...)
     * chay qua runTaskAsynchronously() nen co the van dang chay khi onDisable()
     * goi disconnect() gan nhu ngay lap tuc. Neu dong connection truoc khi cac
     * tac vu do ghi xong: SQLException (o log luc tat server) hoac mat du lieu
     * (vi du log ban cuoi cung truoc khi tat khong bao gio duoc ghi).
     *
     * Sua: dem so tac vu bat dong bo dang chay (tang khi bat dau, giam khi xong
     * qua runAsyncTracked()); disconnect() doi (voi timeout an toan) cho toi khi
     * dem ve 0 truoc khi dong connection.
     */
    private final AtomicInteger pendingAsyncTasks = new AtomicInteger(0);
    private static final long DISCONNECT_WAIT_TIMEOUT_MS = 5000L;
    private static final long DISCONNECT_POLL_INTERVAL_MS = 25L;

    public DatabaseManager(BaB plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, "bab.db");

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS violations_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "uuid TEXT NOT NULL," +
                        "player_name TEXT NOT NULL," +
                        "check_id TEXT NOT NULL," +
                        "vl REAL NOT NULL," +
                        "created_at INTEGER NOT NULL)");

                st.execute("CREATE TABLE IF NOT EXISTS bans_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "uuid TEXT NOT NULL," +
                        "player_name TEXT NOT NULL," +
                        "reason TEXT NOT NULL," +
                        "duration TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL)");

                st.execute("CREATE TABLE IF NOT EXISTS hack_strikes (" +
                        "uuid TEXT PRIMARY KEY," +
                        "player_name TEXT NOT NULL," +
                        "strikes INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL)");

                st.execute("CREATE TABLE IF NOT EXISTS replay_clips (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "uuid TEXT NOT NULL," +
                        "player_name TEXT NOT NULL," +
                        "check_id TEXT NOT NULL," +
                        "world_name TEXT NOT NULL," +
                        "triggered_at INTEGER NOT NULL," +
                        "snapshot_data BLOB NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_replay_uuid ON replay_clips(uuid, triggered_at)");

                // Danh sach nguoi choi da tung xac minh thanh cong (VerificationManager) -
                // de khong bat ho xac minh lai moi lan vao server.
                st.execute("CREATE TABLE IF NOT EXISTS verified_players (" +
                        "uuid TEXT PRIMARY KEY," +
                        "player_name TEXT NOT NULL," +
                        "verified_at INTEGER NOT NULL)");
            }
            plugin.getLogger().info("[BaB] Da ket noi database SQLite (bab.db).");
        } catch (ClassNotFoundException | SQLException ex) {
            plugin.getLogger().severe("[BaB] Khong the khoi tao database: " + ex.getMessage());
            connection = null;
        }
    }

    public void disconnect() {
        if (connection == null) return;

        // Doi cac tac vu bat dong bo dang chay ghi xong truoc khi dong connection
        // (toi da DISCONNECT_WAIT_TIMEOUT_MS de khong treo onDisable() vinh vien
        // neu co tac vu bi ket vi ly do nao do).
        long waited = 0L;
        while (pendingAsyncTasks.get() > 0 && waited < DISCONNECT_WAIT_TIMEOUT_MS) {
            try {
                Thread.sleep(DISCONNECT_POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            waited += DISCONNECT_POLL_INTERVAL_MS;
        }
        if (pendingAsyncTasks.get() > 0) {
            plugin.getLogger().warning("[BaB] Con " + pendingAsyncTasks.get()
                    + " tac vu database chua ghi xong sau " + DISCONNECT_WAIT_TIMEOUT_MS
                    + "ms cho - dong connection ngay, co the mat du lieu gan nhat.");
        }

        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    /**
     * Boc mot tac vu ghi database trong runTaskAsynchronously(), theo doi bang
     * pendingAsyncTasks de disconnect() biet khi nao an toan dong connection.
     */
    private void runAsyncTracked(Runnable task) {
        pendingAsyncTasks.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                task.run();
            } finally {
                pendingAsyncTasks.decrementAndGet();
            }
        });
    }

    /** Doc toan bo so lan vi pham hackcheck da luu (goi 1 lan luc khoi dong, chay dong bo vi bang nho). */
    public Map<UUID, Integer> loadHackStrikesSync() {
        Map<UUID, Integer> result = new HashMap<>();
        if (connection == null) return result;

        String sql = "SELECT uuid, strikes FROM hack_strikes";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getInt("strikes"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("[BaB] Loi doc hack_strikes: " + ex.getMessage());
        }
        return result;
    }

    public void saveHackStrikeAsync(UUID uuid, String playerName, int strikes) {
        if (connection == null) return;
        runAsyncTracked(() -> {
            String sql = "INSERT INTO hack_strikes (uuid, player_name, strikes, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET strikes = excluded.strikes, player_name = excluded.player_name, updated_at = excluded.updated_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setInt(3, strikes);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi ghi hack_strikes: " + ex.getMessage());
            }
        });
    }

    /** Doc toan bo danh sach UUID da tung xac minh thanh cong (goi 1 lan luc khoi dong, dong bo vi bang thuong nho). */
    public Set<UUID> loadVerifiedUuidsSync() {
        Set<UUID> result = new HashSet<>();
        if (connection == null) return result;

        String sql = "SELECT uuid FROM verified_players";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    result.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("[BaB] Loi doc verified_players: " + ex.getMessage());
        }
        return result;
    }

    /** Ghi lai 1 nguoi choi vua xac minh thanh cong (bat dong bo, khong chan main thread). */
    public void saveVerifiedUuidAsync(UUID uuid, String playerName) {
        if (connection == null) return;
        runAsyncTracked(() -> {
            String sql = "INSERT INTO verified_players (uuid, player_name, verified_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, verified_at = excluded.verified_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi ghi verified_players: " + ex.getMessage());
            }
        });
    }

    public void logViolationAsync(UUID uuid, String playerName, String checkId, double vl) {
        if (connection == null) return;
        runAsyncTracked(() -> {
            String sql = "INSERT INTO violations_log (uuid, player_name, check_id, vl, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, checkId);
                ps.setDouble(4, vl);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi ghi violations_log: " + ex.getMessage());
            }
        });
    }

    public void logBanAsync(UUID uuid, String playerName, String reason, String duration) {
        if (connection == null) return;
        runAsyncTracked(() -> {
            String sql = "INSERT INTO bans_log (uuid, player_name, reason, duration, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, reason);
                ps.setString(4, duration);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi ghi bans_log: " + ex.getMessage());
            }
        });
    }

    /**
     * Luu 1 doan ghi replay vao database (nen GZIP truoc khi luu de tiet kiem dung
     * luong, vi day la du lieu se ton tai lau dai). Sau khi luu, tu dong xoa bot
     * cac doan ghi cu nhat cua nguoi choi do neu vuot qua maxClipsToKeep.
     */
    public void saveReplayClipAsync(UUID uuid, String playerName, String checkId, String worldName,
                                     long triggeredAt, List<ReplaySnapshot> snapshots, int maxClipsToKeep) {
        if (connection == null) return;
        runAsyncTracked(() -> {
            byte[] compressed;
            try {
                compressed = compress(serializeSnapshots(snapshots));
            } catch (IOException ex) {
                plugin.getLogger().warning("[BaB] Loi nen du lieu replay: " + ex.getMessage());
                return;
            }

            String insertSql = "INSERT INTO replay_clips (uuid, player_name, check_id, world_name, triggered_at, snapshot_data) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, checkId);
                ps.setString(4, worldName);
                ps.setLong(5, triggeredAt);
                ps.setBytes(6, compressed);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi ghi replay_clips: " + ex.getMessage());
                return;
            }

            String pruneSql = "DELETE FROM replay_clips WHERE uuid = ? AND id NOT IN "
                    + "(SELECT id FROM replay_clips WHERE uuid = ? ORDER BY triggered_at DESC LIMIT ?)";
            try (PreparedStatement ps = connection.prepareStatement(pruneSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, uuid.toString());
                ps.setInt(3, maxClipsToKeep);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi don dep replay_clips: " + ex.getMessage());
            }
        });
    }

    /** Doc danh sach doan ghi cua 1 nguoi choi (cu -> moi), giai nen du lieu, tra ve qua callback tren main thread. */
    public void getReplayClipsAsync(UUID uuid, Consumer<List<ReplayClip>> callback) {
        if (connection == null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(new ArrayList<>()));
            return;
        }
        runAsyncTracked(() -> {
            List<ReplayClip> result = new ArrayList<>();
            String sql = "SELECT player_name, check_id, world_name, triggered_at, snapshot_data "
                    + "FROM replay_clips WHERE uuid = ? ORDER BY triggered_at ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            byte[] data = rs.getBytes("snapshot_data");
                            List<ReplaySnapshot> snapshots = deserializeSnapshots(decompress(data));
                            result.add(new ReplayClip(uuid, rs.getString("player_name"), rs.getString("check_id"),
                                    rs.getLong("triggered_at"), rs.getString("world_name"), snapshots));
                        } catch (IOException ex) {
                            plugin.getLogger().warning("[BaB] Loi giai nen 1 doan replay, bo qua: " + ex.getMessage());
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi doc replay_clips: " + ex.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    /** Xoa cac doan ghi cu hon moc thoi gian cho truoc (dung cho don dep dinh ky theo retention-days). */
    public void purgeOldReplayClipsAsync(long olderThanEpochMs) {
        if (connection == null) return;
        runAsyncTracked(() -> {
            String sql = "DELETE FROM replay_clips WHERE triggered_at < ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, olderThanEpochMs);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("[BaB] Da don dep " + deleted + " doan replay cu (qua han luu tru).");
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[BaB] Loi don dep replay_clips theo thoi gian: " + ex.getMessage());
            }
        });
    }

    private String serializeSnapshots(List<ReplaySnapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        for (ReplaySnapshot s : snapshots) {
            if (sb.length() > 0) sb.append(';');
            sb.append(s.timestampMs).append(',').append(s.x).append(',').append(s.y).append(',')
                    .append(s.z).append(',').append(s.yaw).append(',').append(s.pitch);
        }
        return sb.toString();
    }

    private List<ReplaySnapshot> deserializeSnapshots(String raw) {
        List<ReplaySnapshot> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(",");
            if (parts.length != 6) continue;
            try {
                list.add(new ReplaySnapshot(
                        Long.parseLong(parts[0]),
                        Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                        Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    private byte[] compress(String data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private String decompress(byte[] compressed) throws IOException {
        if (compressed == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
