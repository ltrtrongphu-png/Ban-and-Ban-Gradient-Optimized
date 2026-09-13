package com.example.bab.replay;

import com.example.bab.BaB;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly qua trinh "phat lai" mot ReplayClip cho staff xem: dua staff vao che do
 * Spectator, roi dich chuyen (teleport) HO theo tung khoanh khac da ghi - staff se
 * thay dung nhung gi nguoi bi flag da thay (vi tri + goc nhin) tai thoi diem do.
 */
public class ReplayPlaybackManager {

    private final BaB plugin;
    private final Map<UUID, PlaybackSession> activeSessions = new ConcurrentHashMap<>();

    public ReplayPlaybackManager(BaB plugin) {
        this.plugin = plugin;
    }

    public boolean isWatching(UUID staffUuid) {
        return activeSessions.containsKey(staffUuid);
    }

    public void startPlayback(Player staff, ReplayClip clip) {
        stopPlayback(staff); // Neu dang xem doan khac, dung lai truoc

        World world = Bukkit.getWorld(clip.worldName);
        if (world == null) {
            staff.sendMessage("§cKhong tim thay the gioi '" + clip.worldName + "' (co the da bi xoa/doi ten).");
            return;
        }
        if (clip.snapshots.isEmpty()) {
            staff.sendMessage("§cDoan ghi nay khong co du lieu.");
            return;
        }

        PlaybackSession session = new PlaybackSession();
        session.originalLocation = staff.getLocation();
        session.originalGameMode = staff.getGameMode();
        session.clip = clip;
        session.index = 0;

        staff.setGameMode(GameMode.SPECTATOR);
        staff.sendMessage("§e[BaB] §7Dang phat lai " + clip.playerName + " (" + clip.checkId + ") - "
                + clip.snapshots.size() + " khung hinh. Go §f/bab replay stop §7de dung.");

        int intervalTicks = plugin.getConfig().getInt("replay.sample-interval-ticks", 4);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.index >= session.clip.snapshots.size()) {
                staff.sendMessage("§e[BaB] §7Da phat het doan ghi.");
                stopPlayback(staff);
                return;
            }
            ReplaySnapshot snap = session.clip.snapshots.get(session.index);
            Location loc = new Location(world, snap.x, snap.y, snap.z, snap.yaw, snap.pitch);
            staff.teleport(loc);
            session.index++;
        }, 0L, intervalTicks);

        activeSessions.put(staff.getUniqueId(), session);
    }

    public void stopPlayback(Player staff) {
        PlaybackSession session = activeSessions.remove(staff.getUniqueId());
        if (session == null) return;

        if (session.task != null) session.task.cancel();
        staff.setGameMode(session.originalGameMode);
        staff.teleport(session.originalLocation);
        staff.sendMessage("§e[BaB] §7Da thoat che do xem lai.");
    }

    /** Goi khi staff thoat server dot ngot trong luc dang xem, tranh ro rendering task. */
    public void forceStop(UUID staffUuid) {
        PlaybackSession session = activeSessions.remove(staffUuid);
        if (session != null && session.task != null) session.task.cancel();
    }

    private static class PlaybackSession {
        Location originalLocation;
        GameMode originalGameMode;
        ReplayClip clip;
        int index;
        BukkitTask task;
    }
}
