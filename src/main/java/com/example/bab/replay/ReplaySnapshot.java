package com.example.bab.replay;

import java.util.List;
import java.util.UUID;

/** Mot khoanh khac vi tri/goc nhin duoc ghi lai tai 1 thoi diem. */
public class ReplaySnapshot {
    public final long timestampMs;
    public final double x, y, z;
    public final float yaw, pitch;

    public ReplaySnapshot(long timestampMs, double x, double y, double z, float yaw, float pitch) {
        this.timestampMs = timestampMs;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
