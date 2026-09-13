package com.example.bab.hackcheck;

import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Quet mod/client hack bang ky thuat "Sign Translation Key":
 * 1) Dat 1 tam bien THAT (khong phai gia lap qua packet nua) tai vi tri nguoi
 *    choi khong nhin thay (duoi day the gioi), voi noi dung 4 dong la 4
 *    translation key can kiem tra, dang Component.translatable(key).
 * 2) Goi Player#openSign(...) - day la API CHINH THUC cua Paper, de chinh Paper
 *    tu dong dong goi cac goi tin can thiet (bao gom ca truong "loai block-entity"
 *    ma phien ban truoc bi thieu, gay crash ket noi cho nguoi choi).
 * 3) Client se TU DICH cac key thanh chu that NEU no co mod dang ky key do, va
 *    gui lai qua SignChangeEvent khi man hinh sua bien dong lai.
 * 4) So sanh noi dung tra ve voi key goc: khac nhau => mod duoc dang ky => gan
 *    nhu chac chan co cai mod do.
 *
 * PHIEN BAN NAY DA BO YEU CAU PROTOCOLLIB cho rieng tinh nang nay (van dung cho
 * InvalidPacketCheck o noi khac) - dung toan bo API cong khai cua Paper, on dinh
 * hon nhieu so vi ghep packet thu cong.
 *
 * LUU Y CHUA KIEM CHUNG DUOC 100% (khong co server that de test): sau khi mo,
 * server chu dong goi lenh dong man hinh ngay lap tuc de co gang lam qua trinh
 * nay khong hien thi ra man hinh nguoi choi - nhung dieu nay chua duoc xac nhan
 * hoat dong hoan toan "vo hinh" tren moi truong hop. Neu ban thay man hinh sua
 * bien thoang qua xuat hien khi bi quet, do la dau hieu can bao lai de tinh
 * chinh them.
 */
public class HackDetectionManager implements Listener, ConfigReloadable {

    private final BaB plugin;
    private final List<HackSignature> signatures = new ArrayList<>();
    private final Map<UUID, PendingScan> pendingScans = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> strikeCounts = new ConcurrentHashMap<>();
    private boolean available = false;

    private volatile int timeoutTicks;
    private volatile boolean autoCheckOnJoin;
    private volatile boolean verificationEnabled;
    private volatile int verificationTimeoutSeconds;
    private volatile String alertPermission;
    private volatile String kickMessageTemplate;
    private volatile String punishmentBanDuration;
    private volatile boolean resetStrikesAfterBan;

    public HackDetectionManager(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        timeoutTicks = plugin.getConfig().getInt("hackcheck.timeout-ticks", 30);
        autoCheckOnJoin = plugin.getConfig().getBoolean("hackcheck.auto-check-on-join", false);
        verificationEnabled = plugin.getConfig().getBoolean("verification.enabled", true);
        verificationTimeoutSeconds = plugin.getConfig().getInt("verification.timeout-seconds", 15);
        alertPermission = plugin.getConfig().getString("settings.alert-permission", "bab.alerts");
        kickMessageTemplate = plugin.getConfig().getString("hackcheck.punishment.kick-message",
                "&cPhat hien su dung mod hack (%hacks%).\n&7Day la canh bao LAN 1 - lan sau se bi BAN.");
        punishmentBanDuration = plugin.getConfig().getString("hackcheck.punishment.ban-duration", "30d");
        resetStrikesAfterBan = plugin.getConfig().getBoolean("hackcheck.punishment.reset-strikes-after-ban", true);
    }

    public void setup() {
        // Luon nap so lan vi pham da luu, du tinh nang tu quet co bat hay khong -
        // vi CheckHacksBridge (cau noi voi plugin CheckHacks rieng) cung dung chung
        // he thong kick lan 1/ban lan 2 nay, doc lap voi viec BaB co tu quet hay khong.
        strikeCounts.putAll(plugin.getDatabaseManager().loadHackStrikesSync());

        if (!plugin.getConfig().getBoolean("hackcheck.enabled", true)) {
            plugin.getLogger().info("[BaB] hackcheck.enabled = false trong config.yml - tinh nang TU QUET hack bi tat"
                    + " (he thong kick/ban van hoat dong binh thuong neu dung qua CheckHacksBridge).");
            return;
        }

        loadSignatures();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.available = true;
        plugin.getLogger().info("[BaB] Tinh nang quet hack da san sang voi " + signatures.size() + " chu ky.");
    }

    /**
     * Goi tu CheckHacksBridge khi mot plugin quet hack KHAC (khong phai BaB tu quet)
     * bao co phat hien mod hack. Tai su dung DUNG y het he thong kick lan 1/ban lan 2
     * ma BaB tu quet cung dung, chi khac nguon du lieu dau vao.
     */
    public void handleExternalDetection(Player player, List<String> modNames) {
        List<HackSignature> pseudo = new ArrayList<>();
        for (String name : modNames) {
            pseudo.add(new HackSignature(name, name, name));
        }
        handleAutoDetection(player, pseudo);
    }

    public boolean isAvailable() {
        return available;
    }

    private void loadSignatures() {
        File file = new File(plugin.getDataFolder(), "hacks.yml");
        if (!file.exists()) {
            plugin.saveResource("hacks.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream defStream = plugin.getResource("hacks.yml")) {
            if (defStream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
        }

        if (config.getConfigurationSection("signatures") == null) return;
        for (String id : config.getConfigurationSection("signatures").getKeys(false)) {
            String display = config.getString("signatures." + id + ".display", id);
            List<String> keys = config.getStringList("signatures." + id + ".keys");
            if (keys.isEmpty()) {
                String singleKey = config.getString("signatures." + id + ".key", null);
                if (singleKey != null) keys = java.util.Collections.singletonList(singleKey);
            }
            if (!keys.isEmpty()) {
                signatures.add(new HackSignature(id, display, keys));
            }
        }
    }

    /**
     * Ket qua 1 lan quet: danh sach hack phat hien duoc, VA so lieu ve do tin cay
     * cua ket qua "khong phat hien gi" - phan biet "chac chan sach" voi "khong
     * chac chan vi client khong phan hoi mot phan/toan bo qua trinh quet" (vd do
     * lag, do mod chan giao dien sua bien, hoac do mat ket noi giua chung).
     */
    public static class ScanResult {
        public final List<HackSignature> detected;
        public final int totalKeysChecked;
        public final int keysNoResponse;

        ScanResult(List<HackSignature> detected, int totalKeysChecked, int keysNoResponse) {
            this.detected = detected;
            this.totalKeysChecked = totalKeysChecked;
            this.keysNoResponse = keysNoResponse;
        }

        public boolean isFullyConclusive() {
            return keysNoResponse == 0;
        }
    }

    /** 1 cap (chu ky, 1 trong cac key cua chu ky do) - don vi nho nhat de xep vao 1 dong bien. */
    private static class KeyEntry {
        final HackSignature signature;
        final String key;
        boolean responded = false;

        KeyEntry(HackSignature signature, String key) {
            this.signature = signature;
            this.key = key;
        }
    }

    /**
     * Quet mot nguoi choi. Ket qua (ScanResult) se duoc tra ve qua callback khi
     * hoan tat (chay tren main thread).
     */
    public void scan(Player player, Consumer<ScanResult> onComplete) {
        if (!available) {
            onComplete.accept(new ScanResult(new ArrayList<>(), 0, 0));
            return;
        }
        if (pendingScans.containsKey(player.getUniqueId())) {
            return; // Da co 1 lan quet dang chay cho nguoi nay, tranh chong cheo
        }

        // Trai phang: 1 chu ky co the co nhieu key -> nhieu KeyEntry rieng, moi
        // KeyEntry chiem 1 dong tren bien (toi da 4 dong/bien).
        List<KeyEntry> allKeys = new ArrayList<>();
        for (HackSignature sig : signatures) {
            for (String key : sig.getTranslateKeys()) {
                allKeys.add(new KeyEntry(sig, key));
            }
        }

        PendingScan scan = new PendingScan(player, allKeys, onComplete);
        pendingScans.put(player.getUniqueId(), scan);
        sendNextBatch(scan);
    }

    private void sendNextBatch(PendingScan scan) {
        List<KeyEntry> batch = scan.nextBatch();
        if (batch.isEmpty()) {
            finishScan(scan);
            return;
        }

        Player player = scan.player;
        if (!player.isOnline()) {
            pendingScans.remove(player.getUniqueId());
            return;
        }

        int minHeight = player.getWorld().getMinHeight();
        Location loc = player.getLocation().clone();
        loc.setY(minHeight + 2);
        Block block = loc.getBlock();

        // Luu lai khoi goc de phuc hoi dung y het sau khi quet xong (an toan hon
        // gia dinh no la AIR - o day thuong la stone/bedrock ngam tu nhien).
        scan.originalBlockData = block.getBlockData().clone();
        scan.currentBatchBlock = block;
        scan.currentBatch = batch;

        try {
            block.setType(Material.OAK_SIGN, false);
            org.bukkit.block.BlockState state = block.getState();
            if (!(state instanceof Sign sign)) {
                plugin.getLogger().warning("[BaB] Khong the tao bien tam thoi de quet hack (block state khong phai Sign).");
                revertBlock(scan);
                finishScan(scan);
                return;
            }

            for (int i = 0; i < 4; i++) {
                if (i < batch.size()) {
                    sign.getSide(Side.FRONT).line(i, Component.translatable(batch.get(i).key));
                } else {
                    sign.getSide(Side.FRONT).line(i, Component.empty());
                }
            }
            sign.setWaxed(false);
            sign.update(true, false);

            player.openSign(sign, Side.FRONT);

            // Co gang dong man hinh ngay lap tuc de giam toi da kha nang nguoi
            // choi thay man hinh sua bien thoang qua. Vanilla luon "luu" noi dung
            // hien co trong khung sua bien khi man hinh dong (kieu gi cung the,
            // khong co "huy bo khong luu" nhu hop chat).
            player.closeInventory();
        } catch (Exception ex) {
            plugin.getLogger().warning("[BaB] Loi khi quet hack cho " + player.getName() + ": " + ex.getMessage());
            revertBlock(scan);
            finishScan(scan);
            return;
        }

        // Phong truong hop client khong phan hoi (vi du man hinh khong tu dong
        // dong duoc) - dat timeout de tu chuyen sang batch tiep theo thay vi treo.
        // NANG CAP: truoc day, timeout chi lang le chuyen tiep, KHONG ghi nhan la
        // "khong co phan hoi" - ket qua cuoi cung se bao "khong phat hien" y het
        // nhu khi thuc su quet sach, du 2 truong hop nay do TIN CAY RAT KHAC NHAU.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingScan current = pendingScans.get(player.getUniqueId());
            if (current == scan && current.currentBatch == batch) {
                for (KeyEntry entry : batch) {
                    if (!entry.responded) scan.noResponseCount++;
                }
                revertBlock(scan);
                sendNextBatch(scan);
            }
        }, timeoutTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        PendingScan scan = pendingScans.get(player.getUniqueId());
        if (scan == null || scan.currentBatch == null) return;
        if (!event.getBlock().equals(scan.currentBatchBlock)) return;

        List<KeyEntry> batch = scan.currentBatch;
        for (int i = 0; i < batch.size(); i++) {
            KeyEntry entry = batch.get(i);
            entry.responded = true;
            Component lineComponent = event.line(i);
            String returned = lineComponent == null ? "" : PlainTextComponentSerializer.plainText().serialize(lineComponent);
            // Neu client TRA VE mot chuoi khac voi key goc, nghia la client da "dich"
            // duoc no thanh chu that => mod dang ky key nay dang chay tren client.
            if (!returned.isEmpty() && !returned.equals(entry.key)) {
                if (!scan.detected.contains(entry.signature)) {
                    scan.detected.add(entry.signature);
                }
            }
        }

        event.setCancelled(true); // Khong luu noi dung "gia" nay vao the gioi that
        revertBlock(scan);
        Bukkit.getScheduler().runTask(plugin, () -> sendNextBatch(scan));
    }

    /**
     * FIX (doc D "HackDetectionManager": phat hien khi co day du source thay vi
     * chi bytecode): khong co listener nao xu ly PlayerQuitEvent truoc day. Neu
     * nguoi choi thoat/mat ket noi trong khi dang bi quet (sendNextBatch da dat
     * mot block OAK_SIGN tam thoi de doi client tra loi qua SignChangeEvent):
     *   1) originalBlockData KHONG bao gio duoc phuc hoi -> mot block bien "la"
     *      ton tai vinh vien trong the gioi (o day thuong la khu vuc gan bedrock,
     *      nhung van la 1 thay doi khong mong muon va khong the tu don).
     *   2) pendingScans.get(uuid) KHONG bao gio duoc xoa -> ro ri bo nho vinh vien
     *      giong het pattern da sua trong VerificationManager (savedGameMode).
     * Sua: khi player thoat ma dang co PendingScan, phuc hoi block ngay va xoa
     * entry khoi pendingScans. Task runTaskLater() timeout van co the chay sau do
     * nhung se khong lam gi vi pendingScans.get(uuid) tra ve null (current == scan
     * la false do scan da bi remove).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PendingScan scan = pendingScans.remove(event.getPlayer().getUniqueId());
        if (scan != null) {
            revertBlock(scan);
        }
    }

    private void revertBlock(PendingScan scan) {
        if (scan.currentBatchBlock == null || scan.originalBlockData == null) return;
        try {
            scan.currentBatchBlock.setBlockData(scan.originalBlockData, false);
        } catch (Exception ignored) {
        }
    }

    /** Goi tu su kien join: neu bat auto-check-on-join, len lich quet sau khi verification xong. */
    public void scheduleAutoCheckIfEnabled(Player player) {
        if (!available) return;
        if (!autoCheckOnJoin) return;

        int delaySeconds = verificationEnabled ? verificationTimeoutSeconds + 3 : 3;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            scan(player, result -> {
                if (!result.detected.isEmpty()) {
                    handleAutoDetection(player, result.detected);
                }
            });
        }, delaySeconds * 20L);
    }

    private void handleAutoDetection(Player player, List<HackSignature> detected) {
        String names = detected.stream().map(HackSignature::getDisplayName).collect(Collectors.joining(", "));
        UUID uuid = player.getUniqueId();

        int strikes = strikeCounts.merge(uuid, 1, Integer::sum);
        plugin.getDatabaseManager().saveHackStrikeAsync(uuid, player.getName(), strikes);

        String alertMsg = ChatColor.RED + "[BaB] Phat hien mod hack o " + player.getName()
                + ": " + names + ChatColor.GRAY + " (vi pham lan " + strikes + ")";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alertPermission)) staff.sendMessage(alertMsg);
        }
        plugin.getLogger().warning("[HackCheck] " + player.getName() + " -> " + names + " (lan " + strikes + ")");

        if (strikes <= 1) {
            String kickMsg = ChatColor.translateAlternateColorCodes('&', kickMessageTemplate.replace("%hacks%", names));
            player.kickPlayer(kickMsg);
        } else {
            String reasonTemplate = plugin.getConfig().getString("hackcheck.punishment.ban-reason",
                    "Su dung client hack (%hacks%) - tai pham lan " + strikes);
            String reason = reasonTemplate.replace("%hacks%", names);
            plugin.getBanExecutor().banDirect(player, reason, punishmentBanDuration);

            if (resetStrikesAfterBan) {
                strikeCounts.remove(uuid);
                plugin.getDatabaseManager().saveHackStrikeAsync(uuid, player.getName(), 0);
            }
        }
    }

    private void finishScan(PendingScan scan) {
        pendingScans.remove(scan.player.getUniqueId());
        ScanResult result = new ScanResult(scan.detected, scan.totalKeys, scan.noResponseCount);
        Bukkit.getScheduler().runTask(plugin, () -> scan.onComplete.accept(result));
    }

    /** Trang thai cua 1 lan quet dang dien ra cho 1 nguoi choi. */
    private static class PendingScan {
        final Player player;
        final List<KeyEntry> queue;
        final int totalKeys;
        final List<HackSignature> detected = new ArrayList<>();
        final Consumer<ScanResult> onComplete;
        int noResponseCount = 0;
        List<KeyEntry> currentBatch;
        Block currentBatchBlock;
        BlockData originalBlockData;

        PendingScan(Player player, List<KeyEntry> queue, Consumer<ScanResult> onComplete) {
            this.player = player;
            this.queue = queue;
            this.totalKeys = queue.size();
            this.onComplete = onComplete;
        }

        List<KeyEntry> nextBatch() {
            List<KeyEntry> batch = new ArrayList<>();
            for (int i = 0; i < 4 && !queue.isEmpty(); i++) {
                batch.add(queue.remove(0));
            }
            return batch;
        }
    }
}
