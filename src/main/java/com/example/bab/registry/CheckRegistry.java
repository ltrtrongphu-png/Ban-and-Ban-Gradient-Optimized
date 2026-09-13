package com.example.bab.registry;

import com.example.bab.BaB;
import com.example.bab.checks.AimAssistCheck;
import com.example.bab.checks.AutoArmorCheck;
import com.example.bab.checks.AutoTotemCheck;
import com.example.bab.checks.BlockReachCheck;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.checks.ContainerReachCheck;
import com.example.bab.checks.FastBreakCheck;
import com.example.bab.checks.FastInteractCheck;
import com.example.bab.checks.FastUseCheck;
import com.example.bab.checks.ImpossibleHitCheck;
import com.example.bab.checks.InvalidPacketCheck;
import com.example.bab.checks.InventoryActionCheck;
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
import com.example.bab.checks.SpiderCheck;
import com.example.bab.checks.StepCheck;
import com.example.bab.checks.TimerCheck;
import com.example.bab.checks.Toggleable;
import com.example.bab.checks.XrayCheck;
import com.example.bab.listeners.AntiSpamListener;
import com.example.bab.listeners.ClientBrandListener;
import com.example.bab.verification.IpRateLimiter;
import com.example.bab.verification.VerificationListener;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Tap trung toan bo logic tao + dang ky check/listener cua BaB, tach ra khoi
 * BaB.java (truoc day BaB.java vua la diem vao plugin, vua tu tay new() +
 * registerEvents() cho ~30 check trong 1 method dai - "God Object" duoc neu
 * trong danh gia truoc do). BaB.java gio chi goi registerAll() va nhan ve
 * danh sach ConfigReloadable de quan ly /bab reload.
 *
 * TOI UU DI KEM: check nao CHI co 1 co "enabled" duy nhat (implements
 * Toggleable) se KHONG duoc dang ky listener neu dang TAT luc khoi dong -
 * thay vi dang ky roi de moi handler tu kiem tra enabled va return som (cach
 * cu van ton chi phi goi ham + dispatch event moi lan du check dang tat).
 *
 * GIOI HAN: check TAT tu luc server khoi dong se can RESTART (khong chi
 * /bab reload) de bat lai, vi listener chua tung duoc dang ky voi Bukkit.
 * Chap nhan duoc: doi lay loi ich chinh (check tat khong ton phi event nao),
 * tranh rui ro cua co che unregister/re-register listener dong tai runtime.
 */
public class CheckRegistry {

    private final BaB plugin;
    private final PluginManager pluginManager;
    private final List<ConfigReloadable> reloadableChecks = new ArrayList<>();

    public CheckRegistry(BaB plugin) {
        this.plugin = plugin;
        this.pluginManager = plugin.getServer().getPluginManager();
    }

    /** Tao + dang ky toan bo check/listener. Goi 1 lan duy nhat tu BaB#onEnable(). */
    public List<ConfigReloadable> registerAll() {
        // Check co NHIEU tinh nang doc lap (khong the tat het bang 1 co "enabled"
        // duy nhat) - LUON dang ky, tung tinh nang con tu quyet dinh bat/tat rieng
        // ben trong (xem cac field *Enabled trong tung class).
        register(new MovementCheck(plugin));
        register(new KillAuraCheck(plugin));
        register(new RotationCheck(plugin));
        register(new InventoryActionCheck(plugin));

        // Check "1 cong tac" (1 co enabled duy nhat quyet dinh toan bo hoat dong) -
        // BO QUA dang ky hoan toan neu tat luc khoi dong.
        registerIfEnabled(new FastBreakCheck(plugin));
        registerIfEnabled(new NoFallCheck(plugin));
        registerIfEnabled(new TimerCheck(plugin));
        registerIfEnabled(new AutoTotemCheck(plugin));
        registerIfEnabled(new ScaffoldCheck(plugin));
        registerIfEnabled(new KnockbackCheck(plugin));
        registerIfEnabled(new FastUseCheck(plugin));
        registerIfEnabled(new NoSlowCheck(plugin));
        registerIfEnabled(new XrayCheck(plugin));
        registerIfEnabled(new SpiderCheck(plugin));
        registerIfEnabled(new StepCheck(plugin));
        registerIfEnabled(new AimAssistCheck(plugin));
        registerIfEnabled(new ImpossibleHitCheck(plugin));
        registerIfEnabled(new AutoArmorCheck(plugin));
        registerIfEnabled(new BlockReachCheck(plugin));
        registerIfEnabled(new FastInteractCheck(plugin));
        registerIfEnabled(new NoSwingCheck(plugin));
        registerIfEnabled(new NoClipCheck(plugin));
        registerIfEnabled(new ContainerReachCheck(plugin));
        registerIfEnabled(new AntiSpamListener(plugin));
        registerIfEnabled(new IpRateLimiter(plugin));

        // Khong co co "enabled" don / tan suat qua thap de viec tat som co gia tri -
        // giu nguyen luon dang ky nhu ban goc.
        pluginManager.registerEvents(new RaidAlertListener(plugin), plugin);
        new InvalidPacketCheck(plugin).register();
        register(new ClientBrandListener(plugin));
        pluginManager.registerEvents(new VerificationListener(plugin), plugin);

        return reloadableChecks;
    }

    /** Dang ky luon - dung cho check co nhieu co "enabled" doc lap ben trong. */
    private <T extends Listener & ConfigReloadable> void register(T check) {
        pluginManager.registerEvents(check, plugin);
        reloadableChecks.add(check);
    }

    /** Chi dang ky neu check dang BAT luc khoi dong (xem gioi han o javadoc lop). */
    private <T extends Listener & ConfigReloadable & Toggleable> void registerIfEnabled(T check) {
        if (!check.isEnabled()) return;
        pluginManager.registerEvents(check, plugin);
        reloadableChecks.add(check);
    }
}
