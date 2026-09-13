package com.example.bab.checks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.example.bab.BaB;
import org.bukkit.entity.Player;

/**
 * Kiem tra BadPackets - lay y tuong tu nhom check "impl/badpackets" cua GrimAC.
 * Doc truc tiep goi tin di chuyen o tang packet (can ProtocolLib) va kiem tra gia
 * tri toa do/goc nhin co phai la NaN hoac Infinity hay khong. Client vanilla that
 * KHONG THE VAT LY tao ra gia tri nay - day la dau hieu gan nhu chac chan 100%
 * (khong phai suy doan thong ke nhu cac check khac) cho thay client dang gui goi
 * tin gia mao/injection, thuong gap o cac exploit gay crash server hoac mot so
 * ky thuat move-packet-spoofing tho so.
 *
 * Chi hoat dong neu server co ProtocolLib. Neu khong co, check nay tu vo hieu hoa
 * (an toan, khong crash plugin) - giong hackcheck.
 */
public class InvalidPacketCheck {

    private final BaB plugin;

    public InvalidPacketCheck(BaB plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().info("[BaB] ProtocolLib khong co san - bo qua check BadPackets (invalidpacket).");
            return;
        }

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                BaB babPlugin = InvalidPacketCheck.this.plugin;
                if (!babPlugin.getConfig().getBoolean("anticheat.checks.invalidpacket.enabled", true)) return;
                Player player = event.getPlayer();
                if (babPlugin.isExempt(player)) return;

                try {
                    if (event.getPacketType() != PacketType.Play.Client.LOOK) {
                        double x = event.getPacket().getDoubles().read(0);
                        double y = event.getPacket().getDoubles().read(1);
                        double z = event.getPacket().getDoubles().read(2);
                        if (!isFinite(x) || !isFinite(y) || !isFinite(z)) {
                            babPlugin.flag(player, "invalidpacket", "BadPackets (toa do NaN/Infinity)");
                            event.setCancelled(true);
                            return;
                        }
                    }
                    if (event.getPacketType() != PacketType.Play.Client.POSITION) {
                        float yaw = event.getPacket().getFloat().read(0);
                        float pitch = event.getPacket().getFloat().read(1);
                        if (!isFinite(yaw) || !isFinite(pitch)) {
                            babPlugin.flag(player, "invalidpacket", "BadPackets (goc nhin NaN/Infinity)");
                            event.setCancelled(true);
                        }
                    }
                } catch (Exception ignored) {
                    // Neu cau truc goi tin khac phien ban khong doc duoc field mong doi, bo qua an toan
                }
            }
        });

        plugin.getLogger().info("[BaB] Check BadPackets (invalidpacket) da san sang.");
    }

    private boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }
}
