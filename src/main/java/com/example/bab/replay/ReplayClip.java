package com.example.bab.replay;

import java.util.List;
import java.util.UUID;

/** Mot doan ghi hoan chinh (danh sach cac khoanh khac) gan voi 1 lan nguoi choi bi flag. */
public class ReplayClip {
    public final UUID playerUuid;
    public final String playerName;
    public final String checkId;
    public final long triggeredAtMs;
    public final String worldName;
    public final List<ReplaySnapshot> snapshots;

    public ReplayClip(UUID playerUuid, String playerName, String checkId, long triggeredAtMs,
                       String worldName, List<ReplaySnapshot> snapshots) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.checkId = checkId;
        this.triggeredAtMs = triggeredAtMs;
        this.worldName = worldName;
        this.snapshots = snapshots;
    }
}
