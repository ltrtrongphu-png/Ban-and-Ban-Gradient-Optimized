package com.example.bab.esp;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.example.bab.BaB;
import com.example.bab.checks.ConfigReloadable;
import com.example.bab.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ESP-protection: mot lop phong ve BO SUNG cho AntiFreeCamManager (khong thay
 * the). AntiFreeCamManager "pha" gia tri cua FreeCam/ESP bang Blindness that;
 * module nay nham vao truong hop khac - che gia tri KHOI (block) nhay cam
 * (quang quy...) khoi goi tin BLOCK_CHANGE / MULTI_BLOCK_CHANGE gui cho MOT
 * NGUOI CHOI CU THE trong luc chinh nguoi choi do dang o do sau nhat dinh,
 * bang cach thay bang mot khoi "gia" (mac dinh: DEEPSLATE) truoc khi goi tin
 * roi khoi server.
 *
 * TU VIET LAI HOAN TOAN TU DAU - khong dung lai deepslate.java (file do tu
 * nhan la dich nguoc tu bytecode khong ro nguon goc, nen khong duoc tai su
 * dung). Y tuong ky thuat (gia mao goi tin block o do sau) la ky thuat pho
 * bien, cong khai, giong cach Orebfuscator/AntiXray hoat dong - khong phai
 * sao chep tu 1 san pham cu the nao.
 *
 * GIOI HAN QUAN TRONG (can biet truoc khi bat):
 * - Module nay CHI can thiep goi tin BLOCK_CHANGE va MULTI_BLOCK_CHANGE, tuc
 *   la khoi bi thay/pha huy TRONG LUC nguoi choi dang online va o gan do (vi
 *   du: nguoi choi khac dao lo ra quang gan do). No KHONG rewrite lai toan bo
 *   du lieu chunk ban dau (goi tin MAP_CHUNK) - viec do doi hoi thao tac truc
 *   tiep tren cau truc chunk section/palette o tang NMS, rat phu thuoc phien
 *   ban server va de gay loi/crash neu lam khong ky. Vi vay day la lop phong
 *   ve "nhe" (bo sung, khong thay the mot plugin AntiXray chuyen dung nhu
 *   Orebfuscator neu ban can chan X-ray tu luc chunk vua duoc tai).
 * - Mac dinh TAT (an toan). Bat trong config.yml muc "esp-protection.enabled".
 */
public class EspProtectionManager implements Listener, ConfigReloadable {

    private static final Set<Material> SENSITIVE_BLOCKS = EnumSet.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.SPAWNER, Material.CHEST, Material.TRAPPED_CHEST
    );

    private final BaB plugin;
    private ProtocolManager protocolManager;

    // Trang thai "che do an" hien tai cua tung nguoi choi (dua tren Y THAT cua
    // chinh ho, co do tre/hysteresis de tranh nhap nhay lien tuc o gan ranh gioi).
    private final Set<UUID> hiddenModePlayers = ConcurrentHashMap.newKeySet();

    private volatile double enterHideY;
    private volatile double exitHideY;
    private volatile boolean debug;

    public EspProtectionManager(BaB plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    @Override
    public void loadConfigValues() {
        enterHideY = ConfigUtil.getBoundedDouble(plugin, "esp-protection.enter-hide-y", 30, -2032, 2032);
        exitHideY = ConfigUtil.getBoundedDouble(plugin, "esp-protection.exit-hide-y", 36, -2032, 2032);
        debug = plugin.getConfig().getBoolean("esp-protection.debug", false);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("esp-protection.enabled", false)) return;
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().info("[BaB] ProtocolLib khong co san - bo qua esp-protection.");
            return;
        }

        this.protocolManager = ProtocolLibrary.getProtocolManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPacketListener();
        plugin.getLogger().info("[BaB] esp-protection (che khoi nhay cam o do sau) da bat.");
    }

    public void stop() {
        hiddenModePlayers.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        if (plugin.isExempt(player)) return;

        double y = event.getTo().getY();
        UUID id = player.getUniqueId();

        // Nguong kep (hysteresis): xuong duoi enterY moi BAT che do an, phai len
        // tren exitY (cao hon enterY) moi TAT - tranh nhap nhay khi dung sat 1 ranh gioi.
        if (!hiddenModePlayers.contains(id) && y < enterHideY) {
            hiddenModePlayers.add(id);
        } else if (hiddenModePlayers.contains(id) && y > exitHideY) {
            hiddenModePlayers.remove(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiddenModePlayers.remove(event.getPlayer().getUniqueId());
    }

    private void registerPacketListener() {
        Material fakeMaterial = resolveFakeMaterial();

        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.BLOCK_CHANGE,
                PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player receiver = event.getPlayer();
                if (!hiddenModePlayers.contains(receiver.getUniqueId())) return;

                try {
                    if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
                        maskSingleBlockChange(event, fakeMaterial);
                    } else {
                        maskMultiBlockChange(event, fakeMaterial);
                    }
                } catch (Exception ex) {
                    // Cau truc goi tin khac phien ban server / khong doc duoc field mong
                    // doi -> bo qua an toan, KHONG lam rot goi tin/crash plugin.
                    if (debug) {
                        plugin.getLogger().warning("[BaB] esp-protection: loi khi xu ly goi tin - " + ex);
                    }
                }
            }
        });
    }

    private void maskSingleBlockChange(PacketEvent event, Material fakeMaterial) {
        WrappedBlockData data = event.getPacket().getBlockData().readSafely(0);
        if (data == null) return;
        if (SENSITIVE_BLOCKS.contains(data.getType())) {
            event.getPacket().getBlockData().write(0, WrappedBlockData.createData(fakeMaterial));
        }
    }

    private void maskMultiBlockChange(PacketEvent event, Material fakeMaterial) {
        // CHUA HOAN THIEN O DAY: doi voi MULTI_BLOCK_CHANGE, ten field chinh xac
        // trong ProtocolLib (vi du "getMultiBlockChangeInfoArrays" hay ten khac)
        // thay doi giua cac phien ban ProtocolLib/Minecraft, va minh khong co moi
        // truong build that (paper-api + ProtocolLib that) de bien dich-kiem tra
        // trong sandbox nay. THAY VI doan bua mot ten method co the sai va lam vo
        // build, hay dung `event.getPacket().getModifier()` hoac API tuong ung cho
        // dung phien ban ProtocolLib ban dang dung (xem javadoc cua ban ProtocolLib
        // cu the) truoc khi bat check nay. Tam thoi bo qua an toan (khoi van hien
        // thi that qua goi tin nay, chi khong bi che) cho den khi duoc dien day du.
        if (debug) {
            plugin.getLogger().info("[BaB] esp-protection: MULTI_BLOCK_CHANGE chua duoc trien khai day du - can dien API"
                    + " dung voi phien ban ProtocolLib dang dung truoc khi bat trong production.");
        }
    }

    private Material resolveFakeMaterial() {
        String name = plugin.getConfig().getString("esp-protection.fake-block", "DEEPSLATE");
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[BaB] esp-protection.fake-block khong hop le: '" + name + "' - dung DEEPSLATE.");
            return Material.DEEPSLATE;
        }
    }
}
