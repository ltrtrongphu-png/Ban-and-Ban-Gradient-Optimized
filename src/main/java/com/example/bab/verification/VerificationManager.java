package com.example.bab.verification;

import com.example.bab.BaB;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import com.example.bab.util.ConfigUtil;
import com.example.bab.util.GradientUtil;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * FIX QUAN TRONG (lan 1): ban goc bat MOI nguoi choi phai xac minh MOI LAN vao server,
 * mai mai - khong co co che nho "nguoi nay da xac minh roi". Hau qua: ca nguoi
 * choi cu/quen thuoc cung bi day vao the gioi limbo, phai di chuyen kip trong
 * 15 giay khong thi bi KICK - de bi kick oan do lag/client load chunk cham.
 *
 * Sua: them Set<UUID> verifiedUuids (nap tu DB luc khoi dong + luu lai sau khi
 * xac minh thanh cong). Tu lan thu 2 tro di, nguoi choi da tung xac minh thanh
 * cong se duoc BO QUA hoan toan, vao server binh thuong khong can lam gi ca.
 * Co the tat tinh nang "nho" nay qua config (skip-if-verified: false) neu ban
 * muon bat buoc xac minh lai moi lan (khong khuyen nghi).
 *
 * FIX QUAN TRONG (lan 2): PHAT HIEN QUA LOG - platform BARRIER trong world limbo
 * bi UNLOAD chunk sau khi build xong (khong co gi giu no trong bo nho), vi
 * limboWorld.setAutoSave(false) va khong co force-load. Khi player teleport vao
 * limbo, chunk phai sinh/nap lai va co mot khoang thoi gian KHONG CO SAN duoi
 * chan player -> player roi tu do vai chuc/vai tram block trong khi client van
 * nghi la dang dung yen -> gay ra dung 3 pattern trong log: NoFall (roi 484 block),
 * NoClip (bi ket trong khoi khi server "keo nguoc" player ve dung vi tri sau khi
 * chunk nap xong), Spider (di chuyen doc bat thuong do bi bounce). Anti-cheat
 * rieng cua server (module [BaB] [VL]) khong biet player dang o trong flow
 * verification nen cham diem vi pham va BAN OAN.
 *
 * Sua bang 2 thay doi:
 *   1) Force-load vinh vien cac chunk chua platform ngay sau khi build xong,
 *      dam bao platform khong bao gio bi unload/mat.
 *   2) Ep chunk load DONG BO ngay truoc moi lan teleport, phong truong hop
 *      force-load chua kip hoac world vua duoc reload.
 *   3) Them getLimboWorldName() va isInLimbo(Player) de module anti-cheat khac
 *      co the loai tru (exempt) player dang trong qua trinh xac minh, tranh
 *      an nham false-positive khi co do tre nho luc teleport.
 *
 * FIX QUAN TRONG (lan 3):
 *   1) MEMORY LEAK - savedGameMode chi duoc remove() trong succeed(), khong
 *      duoc don trong fail() hay cancelPending(). Neu player bi kick do het
 *      thoi gian, hoac thoat server giua chung luc dang xac minh, entry cua ho
 *      trong map nay ton tai VINH VIEN du da offline. Voi server dong nguoi va
 *      verification bat buoc moi lan join (khi skip-if-verified=false), map se
 *      phinh to khong gioi han theo thoi gian -> sua bang cach don savedGameMode
 *      trong cleanup() dung chung cho ca 3 nhanh (succeed/fail/cancelPending).
 *   2) Doan "ep chunk load dong bo truoc khi teleport" trong beginVerification()
 *      truoc day CHI load 1 chunk (0,0). Neu platform-size > 16 (half > 8),
 *      platform trai sang cac chunk lan can ma doan nay khong load toi, nen lop
 *      bao ve nay khong che phu het dien tich platform -> sua bang cach load
 *      DONG BO toan bo vung chunk cua platform (giong logic trong buildPlatform),
 *      khong chi mot chunk spawn.
 */
public class VerificationManager {

    private final BaB plugin;
    private World limboWorld;
    private String limboWorldName;
    private final Map<UUID, Double> distanceAccum = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> savedGameMode = new ConcurrentHashMap<>();
    private final Set<UUID> verifiedUuids = new CopyOnWriteArraySet<>();

    public VerificationManager(BaB plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (!plugin.getConfig().getBoolean("verification.enabled", true)) return;

        // Nap danh sach nguoi da tung xac minh thanh cong tu DB (dong bo, chi 1 lan luc khoi dong)
        verifiedUuids.addAll(plugin.getDatabaseManager().loadVerifiedUuidsSync());

        String worldName = plugin.getConfig().getString("verification.world-name", "bab_limbo");
        this.limboWorldName = worldName;
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            limboWorld = existing;
        } else {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator((ChunkGenerator) new VoidGenerator());
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            limboWorld = creator.createWorld();
        }

        if (limboWorld == null) {
            plugin.getLogger().severe("[BaB] Khong the tao the gioi xac minh! Tinh nang verification bi vo hieu hoa.");
            return;
        }

        limboWorld.setAutoSave(false);
        limboWorld.setDifficulty(Difficulty.PEACEFUL);
        limboWorld.setSpawnFlags(false, false);
        buildPlatform();
        plugin.getLogger().info("[BaB] The gioi xac minh '" + worldName + "' da san sang. Da nho " + verifiedUuids.size() + " nguoi choi tung xac minh.");
    }

    private void buildPlatform() {
        // SUGGESTION doc E: platform-size <= 0 khien vong lap for(-half..half) khong
        // chay lan nao -> khong co block BARRIER nao duoc dat, player roi vao khoang
        // khong ngay khi vao limbo world. Bound ve [1, 63] (kich thuoc hop ly, toi da
        // ~4 chunk moi phia) de tranh truong hop nay.
        int size = ConfigUtil.getBoundedInt(plugin, "verification.platform-size", 7, 1, 63);
        int y = 100;
        int half = size / 2;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                limboWorld.getBlockAt(x, y, z).setType(Material.BARRIER);
            }
        }
        limboWorld.setSpawnLocation(0, y + 1, 0);

        // FIX: force-load VINH VIEN cac chunk chua platform, dam bao chung khong
        // bao gio bi server unload roi mat block khi khong co ai dung gan.
        // Neu khong lam buoc nay, lan sau chunk nap lai co the ra "rong" (theo
        // VoidGenerator) va khong con san BARRIER -> player roi vao khoang khong.
        for (Chunk chunk : platformChunks()) {
            chunk.setForceLoaded(true);
        }
    }

    /**
     * Tra ve tat ca cac chunk ma platform chiem dung, dua tren platform-size
     * hien tai trong config. Dung chung boi buildPlatform() (force-load vinh
     * vien) va ensurePlatformChunksLoaded() (load dong bo truoc teleport), de
     * hai noi luon tinh cung mot vung chunk, tranh lech nhau nhu truoc.
     */
    private Iterable<Chunk> platformChunks() {
        int size = ConfigUtil.getBoundedInt(plugin, "verification.platform-size", 7, 1, 63);
        int half = size / 2;
        int minChunk = Math.floorDiv(-half, 16);
        int maxChunk = Math.floorDiv(half, 16);
        java.util.List<Chunk> chunks = new java.util.ArrayList<>();
        for (int cx = minChunk; cx <= maxChunk; cx++) {
            for (int cz = minChunk; cz <= maxChunk; cz++) {
                chunks.add(limboWorld.getChunkAt(cx, cz));
            }
        }
        return chunks;
    }

    /**
     * FIX (lan 3.2): ep load DONG BO toan bo vung chunk cua platform, khong
     * chi mot chunk spawn (0,0). Day la lop bao ve them phong khi world vua
     * duoc reload/re-add ma chua kip force-load, tranh viec player bi tha vao
     * khong trung o ria platform khi platform-size lon hon 16 (trai sang chunk
     * lan can).
     */
    private void ensurePlatformChunksLoaded() {
        for (Chunk chunk : platformChunks()) {
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
        }
    }

    private Location getLimboSpawn() {
        return new Location(limboWorld, 0.5, 101.0, 0.5);
    }

    public boolean isEnabled() {
        return limboWorld != null && plugin.getConfig().getBoolean("verification.enabled", true);
    }

    public boolean isPending(UUID uuid) {
        return distanceAccum.containsKey(uuid);
    }

    /**
     * True neu player hien dang o trong world limbo HOAC dang trong qua trinh
     * xac minh (con dang cho dat du quang duong di chuyen). Cac module khac
     * (vi du anti-cheat) NEN goi ham nay de loai tru player khoi cac kiem tra
     * NoFall/NoClip/Spider... trong luc verification dang dien ra, vi qua trinh
     * teleport + nap chunk co the tao ra chuyen dong bat thuong khong phai hack.
     */
    public boolean isInLimbo(Player player) {
        if (player == null) return false;
        if (limboWorldName != null && player.getWorld().getName().equals(limboWorldName)) {
            return true;
        }
        return isPending(player.getUniqueId());
    }

    public String getLimboWorldName() {
        return limboWorldName;
    }

    public void beginVerification(Player player) {
        if (!isEnabled()) return;
        if (plugin.isExempt(player)) return;

        UUID uuid = player.getUniqueId();

        // FIX: bo qua hoan toan neu da tung xac minh thanh cong truoc day
        boolean skipIfVerified = plugin.getConfig().getBoolean("verification.skip-if-already-verified", true);
        if (skipIfVerified && verifiedUuids.contains(uuid)) {
            return;
        }

        // FIX (lan 3.2): ep TOAN BO vung chunk platform load DONG BO ngay truoc
        // khi teleport, khong chi chunk (0,0). Force-load o buildPlatform() xu
        // ly truong hop chung, nhung buoc nay la lop bao ve them phong khi world
        // vua duoc reload/re-add ma chua kip force-load, tranh viec player bi
        // tha vao khong trung trong khi cho chunk nap - ke ca khi platform trai
        // sang nhieu hon 1 chunk.
        ensurePlatformChunksLoaded();

        savedGameMode.put(uuid, player.getGameMode());
        distanceAccum.put(uuid, 0.0);
        lastLocation.put(uuid, getLimboSpawn());
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(getLimboSpawn());
        player.setFallDistance(0.0f);
        sendInstructions(player);

        // SUGGESTION doc E: timeout <= 0 se kick player gan nhu ngay lap tuc (task chay
        // sau 0 hoac gia tri am tick), khong con thoi gian thuc su de xac minh.
        int timeoutSeconds = ConfigUtil.getBoundedInt(plugin, "verification.timeout-seconds", 25, 1, 3600);
        BukkitTask task = Bukkit.getScheduler().runTaskLater((Plugin) plugin, () -> {
            if (isPending(uuid) && player.isOnline()) {
                fail(player);
            }
        }, timeoutSeconds * 20L);
        timeoutTasks.put(uuid, task);
    }

    private void sendInstructions(Player player) {
        String title = GradientUtil.gradient(
                plugin.getConfig().getString("verification.messages.title-text", "XAC MINH"),
                "#FF0000", "#FFFF00");
        String subtitle = color(plugin.getConfig().getString("verification.messages.subtitle", "&7Di chuyen de xac minh ban la nguoi that"));
        player.sendTitle(title, subtitle, 10, 100, 20);
        String actionbar = color(plugin.getConfig().getString("verification.messages.actionbar", "&7Hay di chuyen xung quanh..."));
        player.sendActionBar(actionbar);
    }

    public void onMove(Player player, Location from, Location to) {
        UUID uuid = player.getUniqueId();
        if (!isPending(uuid)) return;

        Location last = lastLocation.getOrDefault(uuid, to);
        double dx = to.getX() - last.getX();
        double dz = to.getZ() - last.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double newTotal = distanceAccum.merge(uuid, distance, Double::sum);
        lastLocation.put(uuid, to);

        // SUGGESTION doc E: gia tri <= 0 se cho xac minh thanh cong ngay lap tuc khong
        // can di chuyen gi, vo hieu hoa hoan toan muc dich cua buoc xac minh.
        double required = ConfigUtil.getBoundedDouble(plugin, "verification.min-move-distance", 3.0, 0.1, 1000.0);
        if (newTotal >= required) {
            succeed(player);
        }
    }

    private void succeed(Player player) {
        UUID uuid = player.getUniqueId();

        // FIX (lan 3.1): lay gamemode da luu ra TRUOC khi cleanup() xoa no khoi
        // map, vi cleanup() gio don ca savedGameMode (xem ghi chu o cleanup()).
        GameMode original = savedGameMode.get(uuid);
        cleanup(uuid);

        // FIX: ghi nho nguoi nay da xac minh - lan sau vao server se duoc bo qua
        verifiedUuids.add(uuid);
        plugin.getDatabaseManager().saveVerifiedUuidAsync(uuid, player.getName());

        Location realSpawn = resolveRealSpawn();
        player.teleport(realSpawn);
        player.setGameMode(original != null ? original : Bukkit.getDefaultGameMode());
        player.setFallDistance(0.0f);
        player.sendActionBar(color("&aXac minh thanh cong! Chao mung ban."));
    }

    private void fail(Player player) {
        UUID uuid = player.getUniqueId();
        cleanup(uuid);
        String kickMsg = color(plugin.getConfig().getString("verification.messages.kick-timeout", "&cBan khong xac minh kip thoi gian. Vui long ket noi lai."));
        player.kickPlayer(kickMsg);
    }

    public void cancelPending(UUID uuid) {
        cleanup(uuid);
    }

    /**
     * SUGGESTION doc E ("onDisable() khong dung verificationManager/escalationManager"):
     * khong bat buoc ve mat an toan (Bukkit tu huy moi BukkitTask va don server/world
     * khi tat plugin), nhung them vao day de nhat quan voi pattern if(x!=null) x.stop()
     * cua cac manager khac, va de chu dong huy cac task timeout dang cho thay vi de
     * Bukkit lam thay mot cach ngam dinh.
     */
    public void shutdown() {
        for (BukkitTask task : timeoutTasks.values()) {
            if (task != null) task.cancel();
        }
        timeoutTasks.clear();
        distanceAccum.clear();
        lastLocation.clear();
        savedGameMode.clear();
    }

    /**
     * FIX (lan 3.1): don ca savedGameMode o day, dung chung cho ca 3 nhanh goi
     * cleanup() (succeed/fail/cancelPending). Truoc day savedGameMode chi duoc
     * remove() rieng trong succeed(), nen neu player bi kick do timeout (fail)
     * hoac thoat server giua chung (cancelPending, thuong goi tu PlayerQuitEvent
     * o noi khac trong plugin) thi entry cua ho o lai vinh vien trong map ->
     * memory leak dan theo thoi gian, dac biet neu skip-if-verified=false va
     * server dong nguoi phai xac minh lai moi lan join.
     */
    private void cleanup(UUID uuid) {
        distanceAccum.remove(uuid);
        lastLocation.remove(uuid);
        savedGameMode.remove(uuid);
        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private Location resolveRealSpawn() {
        String overrideWorld = plugin.getConfig().getString("verification.real-spawn-world", "");
        if (overrideWorld != null && !overrideWorld.isEmpty()) {
            World w = Bukkit.getWorld(overrideWorld);
            if (w != null) return w.getSpawnLocation();
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
