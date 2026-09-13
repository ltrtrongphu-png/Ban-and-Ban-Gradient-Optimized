package com.example.bab;

import com.example.bab.checks.AimAssistCheck;
import com.example.bab.checks.AutoArmorCheck;
import com.example.bab.checks.BlockReachCheck;
import com.example.bab.checks.ContainerReachCheck;
import com.example.bab.checks.FastBreakCheck;
import com.example.bab.checks.FastInteractCheck;
import com.example.bab.checks.FastUseCheck;
import com.example.bab.checks.ImpossibleHitCheck;
import com.example.bab.checks.InventoryActionCheck;
import com.example.bab.checks.InvalidPacketCheck;
import com.example.bab.checks.KillAuraCheck;
import com.example.bab.checks.KnockbackCheck;
import com.example.bab.checks.MovementCheck;
import com.example.bab.checks.NoClipCheck;
import com.example.bab.checks.NoFallCheck;
import com.example.bab.checks.NoSlowCheck;
import com.example.bab.checks.NoSwingCheck;
import com.example.bab.checks.RaidAlertListener;
import com.example.bab.checks.RotationCheck;
import com.example.bab.checks.ScaffoldCheck;
import com.example.bab.esp.EspProtectionManager;
import com.example.bab.packetevents.PacketEventsBridge;
import com.example.bab.packetevents.PacketFloodCheck;
import com.example.bab.checks.SpiderCheck;
import com.example.bab.checks.StepCheck;
import com.example.bab.checks.TimerCheck;
import com.example.bab.checks.XrayCheck;
import com.example.bab.commands.BabCommand;
import com.example.bab.hackcheck.CheckHacksBridge;
import com.example.bab.hackcheck.HackDetectionManager;
import com.example.bab.listeners.AntiSpamListener;
import com.example.bab.listeners.ClientBrandListener;
import com.example.bab.managers.AlertManager;
import com.example.bab.managers.AntiFreeCamManager;
import com.example.bab.managers.BanExecutor;
import com.example.bab.managers.DatabaseManager;
import com.example.bab.managers.EscalationManager;
import com.example.bab.managers.TpsMonitor;
import com.example.bab.managers.ViolationManager;
import com.example.bab.replay.ReplayPlaybackManager;
import com.example.bab.replay.ReplayRecorder;
import com.example.bab.util.ConfigUtil;
import com.example.bab.verification.IpRateLimiter;
import com.example.bab.verification.VerificationListener;
import com.example.bab.verification.VerificationManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.bab.checks.ConfigReloadable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BaB extends JavaPlugin {

    private ViolationManager violationManager;
    private AlertManager alertManager;
    private BanExecutor banExecutor;
    private DatabaseManager databaseManager;
    private VerificationManager verificationManager;
    private HackDetectionManager hackDetectionManager;
    private CheckHacksBridge checkHacksBridge;
    private ReplayRecorder replayRecorder;
    private ReplayPlaybackManager replayPlaybackManager;
    private TpsMonitor tpsMonitor;
    private AntiFreeCamManager antiFreeCamManager;
    private EscalationManager escalationManager;
    private EspProtectionManager espProtectionManager;
    private PacketFloodCheck packetFloodCheck;

    // Nguoi choi duoc mien kiem tra tam thoi (runtime), doc lap voi permission bab.bypass
    private final Set<UUID> runtimeExempt = Collections.synchronizedSet(new HashSet<>());

    // TOI UU HIEU NANG: cac check cache gia tri config vao field (xem ConfigReloadable)
    // thay vi doc lai getConfig() moi lan su kien xay ra. Danh sach nay duoc goi lai
    // moi khi /bab reload chay, de cache khong bi "cu" so voi config.yml sau khi sua.
    private final List<ConfigReloadable> reloadableChecks = new ArrayList<>();

    @Override
    public void onLoad() {
        // PHAI goi load() cua PacketEvents tai day (onLoad), TRUOC onEnable - xem
        // javadoc cua PacketEventsBridge de biet ly do. Neu jar PacketEvents chua
        // duoc them vao build/server, ham nay tu vo hieu hoa an toan, khong crash BaB.
        PacketEventsBridge.load(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PacketEventsBridge.init();

        this.violationManager = new ViolationManager(this);
        this.alertManager = new AlertManager(this);
        reloadableChecks.add(this.alertManager);
        this.banExecutor = new BanExecutor(this);
        reloadableChecks.add(this.banExecutor);
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        this.verificationManager = new VerificationManager(this);
        this.verificationManager.setup();

        this.hackDetectionManager = new HackDetectionManager(this);
        reloadableChecks.add(this.hackDetectionManager);
        this.hackDetectionManager.setup();

        this.checkHacksBridge = new CheckHacksBridge(this);
        this.checkHacksBridge.register();

        this.replayRecorder = new ReplayRecorder(this);
        this.replayRecorder.start();
        this.replayPlaybackManager = new ReplayPlaybackManager(this);

        // Cac manager moi tham khao tu ban premium: bu tru TPS/lag, chong FreeCam, phat leo thang
        this.tpsMonitor = new TpsMonitor(this);
        reloadableChecks.add(this.tpsMonitor);
        this.tpsMonitor.start();

        this.antiFreeCamManager = new AntiFreeCamManager(this);
        reloadableChecks.add(this.antiFreeCamManager);
        this.antiFreeCamManager.start();

        this.escalationManager = new EscalationManager(this);
        reloadableChecks.add(this.escalationManager);

        // esp-protection: lop phong ve BO SUNG (khong thay the AntiFreeCamManager o
        // tren) - mac dinh TAT, xem esp-protection.enabled trong config.yml.
        this.espProtectionManager = new EspProtectionManager(this);
        reloadableChecks.add(this.espProtectionManager);
        this.espProtectionManager.start();

        // Check moi dua tren PacketEvents (song song voi ProtocolLib, khong thay the).
        // Neu PacketEventsBridge.load() that bai o onLoad() (thieu jar), tu vo hieu hoa.
        if (PacketEventsBridge.isAvailable()) {
            this.packetFloodCheck = new PacketFloodCheck(this);
            if (this.packetFloodCheck.isEnabled()) {
                reloadableChecks.add(this.packetFloodCheck);
                this.packetFloodCheck.register();
            }
        } else {
            getLogger().info("[BaB] PacketEvents khong san sang - bo qua check 'packetflood'.");
        }

        registerListeners();
        registerCommands();

        getLogger().info("BaB da duoc bat. AntiCheat: " + getConfig().getBoolean("anticheat.enabled", true)
                + " | AntiSpam: " + getConfig().getBoolean("antispam.enabled", true));

        String banPluginName = getConfig().getString("settings.ban-plugin-name", "LiteBans");
        if (getConfig().getBoolean("settings.require-ban-plugin", true)
                && getServer().getPluginManager().getPlugin(banPluginName) == null) {
            getLogger().warning("Khong tim thay plugin '" + banPluginName + "'! Cac lenh ban se bi bo qua"
                    + " cho den khi plugin nay duoc cai dat (hoac doi settings.ban-plugin-name trong config.yml).");
        }
    }

    @Override
    public void onDisable() {
        if (replayRecorder != null) replayRecorder.stop();
        if (checkHacksBridge != null) checkHacksBridge.unregister();
        if (antiFreeCamManager != null) antiFreeCamManager.stop();
        if (espProtectionManager != null) espProtectionManager.stop();
        if (packetFloodCheck != null) packetFloodCheck.unregister();
        PacketEventsBridge.terminate();
        if (tpsMonitor != null) tpsMonitor.stop();
        // SUGGESTION doc E: them cho nhat quan voi cac manager khac (khong bat buoc,
        // xem javadoc cua VerificationManager.shutdown()). EscalationManager khong co
        // task/tai nguyen nao can giai phong nen khong can shutdown() tuong ung.
        if (verificationManager != null) verificationManager.shutdown();
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("BaB da tat.");
    }

    private void registerListeners() {
        reloadableChecks.addAll(new com.example.bab.registry.CheckRegistry(this).registerAll());
    }

    private void registerCommands() {
        BabCommand executor = new BabCommand(this);
        getCommand("bab").setExecutor(executor);
        getCommand("bab").setTabCompleter(executor);
    }

    /**
     * Diem vao trung tam cho moi check: cong diem VL, gui canh bao cho staff,
     * va tu dong ban neu vuot nguong cau hinh cho check do.
     *
     * @param player    nguoi choi bi nghi ngo
     * @param checkId   khoa cau hinh (vd "speed", "flight", "killaura"...)
     * @param displayName ten hien thi trong tin nhan canh bao / ly do ban
     */
    public void flag(Player player, String checkId, String displayName) {
        if (!getConfig().getBoolean("anticheat.enabled", true)) return;
        if (isExempt(player)) return;

        // SUGGESTION doc E: weight/threshold am hoac vo ly se lam hong hoan toan logic
        // cham diem (vd threshold=0 -> ban ngay lan dau, weight am -> tru diem thay vi cong).
        double weight = ConfigUtil.getBoundedDouble(this, "anticheat.checks." + checkId + ".weight", 10, 0.0, 100_000.0);
        double threshold = ConfigUtil.getBoundedDouble(this, "anticheat.checks." + checkId + ".ban-threshold", 100, 0.01, 1_000_000.0);

        // Bu tru TPS: khi server lag, giam trong so cong diem de tranh bat oan hang loat
        // nguoi choi cung luc do server khong theo kip, khong phai vi ho dung hack.
        double compensation = tpsMonitor.getCompensationFactor();
        weight = weight / compensation;

        double vl = violationManager.addViolation(player, checkId, weight);
        alertManager.alertViolation(player, displayName, vl, threshold);
        databaseManager.logViolationAsync(player.getUniqueId(), player.getName(), checkId, vl);
        replayRecorder.saveClip(player, checkId);

        if (vl >= threshold) {
            String escalationConfig = getConfig().getString("anticheat.checks." + checkId + ".escalation", "");
            if (escalationConfig.isEmpty()) {
                // Khong khai bao escalation cho check nay -> giu nguyen HANH VI CU 100%
                // (ban thang khi du nguong, reset toan bo VL) de tuong thich nguoc.
                banExecutor.ban(player, displayName, vl);
                violationManager.resetAll(player);
            } else {
                // Co khai bao escalation -> dung he thong phat leo thang moi, chi reset
                // VL cua RIENG check nay (khong dong cham toi cac check khac).
                escalationManager.escalate(player, checkId, displayName, escalationConfig);
                violationManager.resetCheck(player, checkId);
            }
        }
    }

    /**
     * FIX: truoc day isExempt() chi biet permission "bab.bypass" va toggle
     * thu cong runtimeExempt, hoan toan khong biet gi ve trang thai xac minh
     * (verification/limbo). Hau qua: NoFallCheck/NoClipCheck/SpiderCheck (goi
     * isExempt() truoc khi cham diem) van cham diem vi pham cho player dang bi
     * teleport vao world limbo cho chunk nap - dung 3 pattern gay ban oan da
     * ghi trong log goc. VerificationManager da co san isInLimbo(Player) danh
     * rieng cho muc dich nay nhung chua bao gio duoc goi tu day.
     *
     * Sua: hoi them verificationManager.isInLimbo(player). Check null vi
     * isExempt() co the duoc goi tu mot listener truoc khi verificationManager
     * duoc gan xong trong onEnable() (vi du edge case plugin vua reload).
     */
    public boolean isExempt(Player player) {
        if (player.hasPermission("bab.bypass")) return true;
        if (runtimeExempt.contains(player.getUniqueId())) return true;
        if (verificationManager != null && verificationManager.isInLimbo(player)) return true;
        return false;
    }

    public boolean toggleExempt(UUID uuid) {
        if (runtimeExempt.contains(uuid)) {
            runtimeExempt.remove(uuid);
            return false;
        } else {
            runtimeExempt.add(uuid);
            return true;
        }
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public BanExecutor getBanExecutor() {
        return banExecutor;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public VerificationManager getVerificationManager() {
        return verificationManager;
    }

    public HackDetectionManager getHackDetectionManager() {
        return hackDetectionManager;
    }

    public CheckHacksBridge getCheckHacksBridge() {
        return checkHacksBridge;
    }

    public ReplayRecorder getReplayRecorder() {
        return replayRecorder;
    }

    public ReplayPlaybackManager getReplayPlaybackManager() {
        return replayPlaybackManager;
    }

    public TpsMonitor getTpsMonitor() {
        return tpsMonitor;
    }

    public AntiFreeCamManager getAntiFreeCamManager() {
        return antiFreeCamManager;
    }

    public EscalationManager getEscalationManager() {
        return escalationManager;
    }

    /**
     * Goi lai loadConfigValues() tren moi check co cache config (xem
     * ConfigReloadable) - PHAI goi sau reloadConfig() de cache khong bi
     * "cu" so voi config.yml vua sua. Duoc goi tu BabCommand khi /bab reload.
     */
    public void reloadCachedCheckConfigs() {
        for (ConfigReloadable check : reloadableChecks) {
            check.loadConfigValues();
        }
    }
}
