package com.example.bab.commands;

import com.example.bab.BaB;
import com.example.bab.hackcheck.HackSignature;
import com.example.bab.replay.ReplayClip;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /bab vl <player>          - xem VL hien tai cua nguoi choi theo tung check
 * /bab exempt <player>      - bat/tat mien kiem tra tam thoi cho nguoi choi
 * /bab reset <player>       - xoa het VL cua nguoi choi
 * /bab reload               - nap lai config.yml
 *
 * GHI CHU: tu ban nay, cac dong sendMessage/broadcast hien thi cho nguoi choi
 * da duoc viet co dau tieng Viet day du de de doc hon. Comment noi bo van giu
 * khong dau (khong anh huong nguoi dung, thay doi toan bo se rat lon va de sai).
 */
public class BabCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("vl", "exempt", "reset", "reload", "checkhack", "checkall", "replay");
    private static final List<String> CHECK_IDS = Arrays.asList(
            "speed", "flight", "killaura", "autoclicker", "fastbreak", "nofall", "timer",
            "rotation", "scaffold", "knockback", "fastuse", "invaction", "noslow", "invalidpacket",
            "xray", "noclip", "containerreach",
            "spider", "step", "aimassist", "impossiblehit", "autoarmor", "blockreach", "fastinteract", "noswing");

    private final BaB plugin;

    public BabCommand(BaB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bab.admin")) {
            sender.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "vl":
                handleVl(sender, args);
                break;
            case "exempt":
                handleExempt(sender, args);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "reload":
                plugin.reloadConfig();
                // TOI UU HIEU NANG: mot so check (MovementCheck, KillAuraCheck) cache
                // gia tri config vao field de khong phai doc getConfig() moi event -
                // PHAI goi lai sau reloadConfig() de cache khop voi config.yml vua sua,
                // neu khong admin sua config xong se thay /bab reload "khong co tac dung".
                plugin.reloadCachedCheckConfigs();
                sender.sendMessage(ChatColor.GREEN + "[BaB] Đã nạp lại config.yml.");
                break;
            case "checkhack":
                handleCheckHack(sender, args);
                break;
            case "checkall":
                handleCheckAll(sender);
                break;
            case "replay":
                handleReplay(sender, args);
                break;
            default:
                sendUsage(sender);
        }
        return true;
    }

    private void handleVl(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Dùng: /bab vl <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Người chơi không online.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "===== VL của " + target.getName() + " =====");
        for (String check : CHECK_IDS) {
            double vl = plugin.getViolationManager().getVl(target, check);
            double threshold = plugin.getConfig().getDouble("anticheat.checks." + check + ".ban-threshold", 100);
            ChatColor color = vl >= threshold * 0.75 ? ChatColor.RED : (vl > 0 ? ChatColor.YELLOW : ChatColor.GRAY);
            sender.sendMessage(color + "  " + check + ": " + Math.round(vl) + "/" + Math.round(threshold));
        }
    }

    private void handleExempt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Dùng: /bab exempt <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Người chơi không online.");
            return;
        }
        boolean nowExempt = plugin.toggleExempt(target.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "[BaB] " + target.getName()
                + (nowExempt ? " giờ đã được MIỄN kiểm tra." : " không còn được miễn kiểm tra."));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Dùng: /bab reset <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Người chơi không online.");
            return;
        }
        plugin.getViolationManager().resetAll(target);
        sender.sendMessage(ChatColor.GREEN + "[BaB] Đã xóa hết VL của " + target.getName() + ".");
    }

    /**
     * /bab checkall - ra soat lan luot TOAN BO nguoi choi dang online bang cung
     * co che Sign Translation Key voi /bab checkhack (chi khac la chay tu dong
     * cho tung nguoi 1, khong can go ten). Chay TUAN TU (khong quet song song
     * het tat ca) de: (1) tranh spam tao/xoa bien tam thoi cung luc gay giat
     * hinh server voi nhieu nguoi choi, (2) de log/chat message hien thi RO RANG
     * tung nguoi mot, giong phong cach thong bao cua KingMC.
     */
    private void handleCheckAll(CommandSender sender) {
        if (!plugin.getHackDetectionManager().isAvailable()) {
            sender.sendMessage(ChatColor.RED + "Tính năng quét hack chưa sẵn sàng (cần cài ProtocolLib và restart server).");
            return;
        }

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "[BaB] Không có người chơi nào đang online.");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.RED + "[BaB] Rà soát toàn bộ player. Bắt đầu check...");

        int[] detectedCount = {0};
        int[] inconclusiveCount = {0};
        checkAllSequential(sender, targets, 0, detectedCount, inconclusiveCount);
    }

    private void checkAllSequential(CommandSender sender, List<Player> targets, int index, int[] detectedCount, int[] inconclusiveCount) {
        if (index >= targets.size()) {
            Bukkit.broadcastMessage(ChatColor.RED + "[BaB] Hoàn tất rà soát " + targets.size() + " người chơi - phát hiện "
                    + detectedCount[0] + " trường hợp nghi ngờ"
                    + (inconclusiveCount[0] > 0 ? ChatColor.GRAY + " (" + inconclusiveCount[0] + " người không quét được đầy đủ, nên quét lại riêng)" : "") + ".");
            return;
        }

        Player target = targets.get(index);
        if (!target.isOnline()) {
            checkAllSequential(sender, targets, index + 1, detectedCount, inconclusiveCount);
            return;
        }

        sender.sendMessage(ChatColor.RED + "[BaB] Đang check " + target.getName() + "...");
        plugin.getHackDetectionManager().scan(target, result -> {
            if (!result.detected.isEmpty()) {
                detectedCount[0]++;
                String names = result.detected.stream().map(HackSignature::getDisplayName).collect(Collectors.joining(", "));
                Bukkit.broadcastMessage(ChatColor.RED + "[BaB] Phát hiện ở " + target.getName() + ": " + names);
            }
            if (!result.isFullyConclusive()) {
                inconclusiveCount[0]++;
            }
            // Chuyen sang nguoi tiep theo SAU KHI ket qua nguoi nay ve (callback bat
            // dong bo) - dam bao khong quet chong cheo, giong het cach checkhack don le hoat dong.
            checkAllSequential(sender, targets, index + 1, detectedCount, inconclusiveCount);
        });
    }

    private void handleCheckHack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Dùng: /bab checkhack <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Người chơi không online.");
            return;
        }
        if (!plugin.getHackDetectionManager().isAvailable()) {
            sender.sendMessage(ChatColor.RED + "Tính năng quét hack chưa sẵn sàng (cần cài ProtocolLib và restart server).");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "[BaB] Đang quét " + target.getName() + "... (mất vài giây)");
        plugin.getHackDetectionManager().scan(target, result -> {
            if (result.detected.isEmpty()) {
                if (result.isFullyConclusive()) {
                    sender.sendMessage(ChatColor.GREEN + "[BaB] Không phát hiện mod hack nào ở " + target.getName() + ".");
                } else {
                    // NANG CAP: truoc day truong hop nay bao y het "khong phat hien" du
                    // client KHONG PHAN HOI mot phan qua trinh quet - de gay hieu lam
                    // "da quet sach" trong khi that ra chua chac chan. Gio bao ro cho
                    // staff biet ket qua nay KHONG DAY DU DO TIN CAY.
                    sender.sendMessage(ChatColor.YELLOW + "[BaB] Không phát hiện mod hack, NHƯNG " + result.keysNoResponse
                            + "/" + result.totalKeysChecked + " key không nhận được phản hồi từ client "
                            + ChatColor.GRAY + "(do lag, mất kết nối, hoặc client chặn màn hình sửa biển) - kết quả CHƯA CHẮC CHẮN, nên thử quét lại.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "[BaB] Phát hiện " + result.detected.size() + " dấu hiệu ở " + target.getName() + ":");
                for (HackSignature h : result.detected) {
                    sender.sendMessage(ChatColor.RED + "  - " + h.getDisplayName());
                }
                if (!result.isFullyConclusive()) {
                    sender.sendMessage(ChatColor.GRAY + "  (" + result.keysNoResponse + "/" + result.totalKeysChecked
                            + " key khác không phản hồi - có thể còn mod chưa được phát hiện thêm)");
                }
            }
        });
    }

    private void handleReplay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Dùng: /bab replay <player|stop> [số-thứ-tự]");
            return;
        }

        if (args[1].equalsIgnoreCase("stop")) {
            if (!(sender instanceof Player staffPlayer)) {
                sender.sendMessage(ChatColor.RED + "Lệnh này chỉ dành cho người chơi trong game.");
                return;
            }
            plugin.getReplayPlaybackManager().stopPlayback(staffPlayer);
            return;
        }

        UUID targetUuid = plugin.getReplayRecorder().findUuidByName(args[1]);
        if (targetUuid == null) {
            sender.sendMessage(ChatColor.RED + "Không tìm thấy người chơi này (chưa từng vào server?).");
            return;
        }
        String targetName = args[1];

        if (args.length < 3) {
            // Liet ke danh sach - doc bat dong bo tu database
            sender.sendMessage(ChatColor.GRAY + "Đang tải danh sách đoạn ghi...");
            plugin.getReplayRecorder().getClipsAsync(targetUuid, clips -> {
                if (clips.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Người chơi này chưa có đoạn ghi nào (chỉ lưu khi bị flag).");
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "===== Đoạn ghi của " + targetName + " (" + clips.size() + ") =====");
                for (int i = 0; i < clips.size(); i++) {
                    ReplayClip c = clips.get(i);
                    long secondsAgo = (System.currentTimeMillis() - c.triggeredAtMs) / 1000;
                    sender.sendMessage(ChatColor.YELLOW + "  [" + i + "] " + ChatColor.GRAY
                            + c.checkId + " - " + secondsAgo + "s trước (" + c.snapshots.size() + " khung hình)");
                }
                sender.sendMessage(ChatColor.GRAY + "Dùng: /bab replay " + targetName + " <số-thứ-tự> để xem lại.");
            });
            return;
        }

        if (!(sender instanceof Player staffPlayer)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ dành cho người chơi trong game.");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Số thứ tự không hợp lệ.");
            return;
        }

        sender.sendMessage(ChatColor.GRAY + "Đang tải đoạn ghi...");
        plugin.getReplayRecorder().getClipsAsync(targetUuid, clips -> {
            if (index < 0 || index >= clips.size()) {
                staffPlayer.sendMessage(ChatColor.RED + "Số thứ tự không tồn tại. Dùng /bab replay " + targetName + " để xem danh sách.");
                return;
            }
            plugin.getReplayPlaybackManager().startPlayback(staffPlayer, clips.get(index));
        });
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== BaB =====");
        sender.sendMessage(ChatColor.YELLOW + "/bab vl <player>" + ChatColor.GRAY + " - Xem VL của người chơi");
        sender.sendMessage(ChatColor.YELLOW + "/bab exempt <player>" + ChatColor.GRAY + " - Bật/tắt miễn kiểm tra");
        sender.sendMessage(ChatColor.YELLOW + "/bab reset <player>" + ChatColor.GRAY + " - Xóa hết VL");
        sender.sendMessage(ChatColor.YELLOW + "/bab reload" + ChatColor.GRAY + " - Nạp lại config.yml");
        sender.sendMessage(ChatColor.YELLOW + "/bab checkhack <player>" + ChatColor.GRAY + " - Quét mod hack (cần ProtocolLib)");
        sender.sendMessage(ChatColor.YELLOW + "/bab checkall" + ChatColor.GRAY + " - Rà soát mod hack cho TOÀN BỘ người chơi online");
        sender.sendMessage(ChatColor.YELLOW + "/bab replay <player>" + ChatColor.GRAY + " - Xem danh sách đoạn ghi khi bị flag");
        sender.sendMessage(ChatColor.YELLOW + "/bab replay <player> <số>" + ChatColor.GRAY + " - Phát lại đoạn ghi (bạn sẽ vào Spectator)");
        sender.sendMessage(ChatColor.YELLOW + "/bab replay stop" + ChatColor.GRAY + " - Dừng xem lại, quay về vị trí cũ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bab.admin")) return new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 2 && Arrays.asList("vl", "exempt", "reset", "checkhack", "replay").contains(args[0].toLowerCase())) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
