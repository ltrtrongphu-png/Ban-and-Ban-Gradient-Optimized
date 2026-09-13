package com.example.bab.checks;

import com.example.bab.BaB;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FIX QUAN TRONG: dao trung quang "bi chon kin hoan toan" la hanh vi HOAN TOAN
 * BINH THUONG cua nguoi choi dao ham co he thong (strip-mining) hoac chi la may
 * man - KHONG phai dau hieu dang tin cay cua x-ray tu 1 lan duy nhat. Ban goc
 * cong VL truc tiep tu 1 lan trung quang, de ban oan rat nhieu nguoi dao binh thuong.
 *
 * NANG CAP: thay vi CHI canh bao staff tung lan rieng le (khong co ngu canh -
 * staff kho danh gia 1 dong log don le co dang ngo hay khong), gio THEO DOI
 * TY LE quang-quy/tong-so-khoi-dao THEO PHIEN cho tung nguoi choi - day moi la
 * cach cac anticheat x-ray THUC SU dang tin cay hoat dong (vd Boinbot AntiXray,
 * Watchdog cua Hypixel - cong khai, khong lay tu 1 plugin cu the): nguoi dao
 * binh thuong co ty le quang-quy/tong-khoi RAT THAP (hau het la da/dat thuong),
 * con nguoi dung x-ray de dao THANG toi quang se co ty le nay cao bat thuong.
 * 1 lan trung quang bi chon van khong tu no co y nghia (van chi canh bao, KHONG
 * cong VL/ban), nhung KEM THEM ty le tich luy giup staff danh gia CHINH XAC HON
 * NHIEU so voi chi 1 dong log don le nhu truoc.
 */
public class XrayCheck implements Listener, ConfigReloadable, Toggleable {

    private static final Set<Material> VALUABLE_ORES = EnumSet.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE
    );

    private final BaB plugin;
    // Thong ke THEO PHIEN (reset khi thoat server) - khong luu lau dai, chi de
    // ho tro staff danh gia trong luc dang xem xet, khong dung de auto-ban.
    private final Map<UUID, AtomicInteger> totalBlocksMined = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> hiddenOresFound = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int minBlocksForRatio;
    private volatile String alertPermission;

    public XrayCheck(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        enabled = plugin.getConfig().getBoolean("anticheat.checks.xray.enabled", true);
        minBlocksForRatio = plugin.getConfig().getInt("anticheat.checks.xray.min-blocks-for-ratio", 200);
        alertPermission = plugin.getConfig().getString("settings.alert-permission", "bab.alerts");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        UUID uuid = player.getUniqueId();
        Block block = event.getBlock();

        // Chi dem cac khoi "co dang tin cay" (loai bo la cay/co - vo tuc thi,
        // khong lien quan gi den dao ham) de ty le khong bi pha loang sai.
        if (block.getType().getHardness() > 0f) {
            totalBlocksMined.computeIfAbsent(uuid, k -> new AtomicInteger()).incrementAndGet();
        }

        if (!VALUABLE_ORES.contains(block.getType())) return;
        if (!isFullyHidden(block)) return;

        int hiddenCount = hiddenOresFound.computeIfAbsent(uuid, k -> new AtomicInteger()).incrementAndGet();
        int totalMined = totalBlocksMined.getOrDefault(uuid, new AtomicInteger(1)).get();
        double ratioPercent = totalMined > 0 ? (hiddenCount * 100.0 / totalMined) : 0;

        // CHI canh bao, KHONG goi plugin.flag() (khong cong VL, khong the dan toi ban) -
        // gio day KEM THEM ty le tich luy trong phien de staff co ngu canh danh gia.
        String ratioNote = totalMined >= minBlocksForRatio
                ? String.format(" §7| ty le quang an/tong khoi dao trong phien: %.2f%% (%d/%d)", ratioPercent, hiddenCount, totalMined)
                : " §7| (chua du du lieu de tinh ty le dang tin cay trong phien nay)";

        String msg = "§e[BaB] " + player.getName() + " dao trung " + prettyName(block.getType())
                + " bi chon kin hoan toan §7(CHI LA GOI Y THAM KHAO, KHONG tu dong ban)" + ratioNote;
        plugin.getLogger().info(msg.replaceAll("§.", ""));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alertPermission)) staff.sendMessage(msg);
        }
    }

    private boolean isFullyHidden(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (isOpenSpace(block.getRelative(face).getType())) return false;
        }
        return true;
    }

    private boolean isOpenSpace(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR
                || m == Material.WATER || m == Material.LAVA || !m.isSolid();
    }

    private String prettyName(Material m) {
        return m.name().replace("DEEPSLATE_", "").replace("_ORE", "").replace("_", " ").toLowerCase();
    }

    // FIX (memory leak): don entry cua nguoi choi khoi 2 map tren khi thoat server -
    // thong ke chi co y nghia THEO PHIEN, khong can/khong nen luu qua cac lan join.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        totalBlocksMined.remove(uuid);
        hiddenOresFound.remove(uuid);
    }
}

