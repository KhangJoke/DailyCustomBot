package unicorn.bot.dailycustombot.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service xử lý logic tạo/cập nhật team cho UNICORN CHAMPIONSHIP.
 * Hỗ trợ 2 flow:
 * - CREATE: Tạo mới team (channel + role + welcome message)
 * - UPDATE: Cập nhật team (diff members, thu hồi/gán role, cập nhật permission)
 */
public class TeamService {

    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    // ============================================================
    // CẤU HÌNH — Giống ConfirmTeamCommand.java
    // ============================================================
    private static final String ID_ROLE_TUYEN_THU = "1493680420498444449";
    private static final String ID_ROLE_DOI_TRUONG = "1493680761142907022";
    private static final String ID_ROLE_ADMIN = "1477129745543204935";
    private static final String ID_CHANNEL_CHECKIN = "1493684466311364690";
    private static final String ID_CATEGORY_TEAMS = "1461604155687702743";

    private final JDA jda;

    public TeamService(JDA jda) {
        this.jda = jda;
    }

    /**
     * Xử lý request confirm team — tự động phân biệt CREATE vs UPDATE.
     */
    public ConfirmTeamResponse confirmTeam(ConfirmTeamRequest request) {
        // Validate input
        if (request.teamName() == null || request.teamName().isBlank()) {
            return ConfirmTeamResponse.error("Thiếu tên đội (teamName).");
        }
        if (request.shortName() == null || request.shortName().isBlank()) {
            return ConfirmTeamResponse.error("Thiếu tên viết tắt (shortName).");
        }
        if (request.captainDiscord() == null || request.captainDiscord().isBlank()) {
            return ConfirmTeamResponse.error("Thiếu Discord ID đội trưởng (captainDiscord).");
        }
        if (request.membersDiscord() == null || request.membersDiscord().isEmpty()) {
            return ConfirmTeamResponse.error("Thiếu danh sách thành viên (membersDiscord).");
        }

        // Lấy guild (bot chỉ ở 1 server)
        Guild guild = jda.getGuilds().isEmpty() ? null : jda.getGuilds().get(0);
        if (guild == null) {
            return ConfirmTeamResponse.error("Bot chưa tham gia server nào!");
        }

        // Validate roles
        Role roleTuyenThu = guild.getRoleById(ID_ROLE_TUYEN_THU);
        Role roleDoiTruong = guild.getRoleById(ID_ROLE_DOI_TRUONG);
        Role roleAdmin = guild.getRoleById(ID_ROLE_ADMIN);

        if (roleTuyenThu == null) {
            return ConfirmTeamResponse.error("Không tìm thấy Role Tuyển Thủ (ID: " + ID_ROLE_TUYEN_THU + ").");
        }
        if (roleDoiTruong == null) {
            return ConfirmTeamResponse.error("Không tìm thấy Role Đội Trưởng (ID: " + ID_ROLE_DOI_TRUONG + ").");
        }
        if (roleAdmin == null) {
            return ConfirmTeamResponse.error("Không tìm thấy Role Admin (ID: " + ID_ROLE_ADMIN + ").");
        }

        // Validate category
        Category teamCategory = guild.getCategoryById(ID_CATEGORY_TEAMS);
        if (teamCategory == null) {
            return ConfirmTeamResponse.error("Không tìm thấy Category team (ID: " + ID_CATEGORY_TEAMS + ").");
        }

        // ─── Tìm captain member ───
        Member captainMember = findMemberByUsername(guild, request.captainDiscord());
        if (captainMember == null) {
            return ConfirmTeamResponse.error("Không tìm thấy đội trưởng '" + request.captainDiscord()
                    + "' trong server. Kiểm tra lại Discord username.");
        }

        // ─── Tìm tất cả members ───
        List<Member> newMembers = new ArrayList<>();
        newMembers.add(captainMember);
        List<String> notFound = new ArrayList<>();

        for (String username : request.membersDiscord()) {
            if (username == null || username.isBlank())
                continue;
            String cleanName = username.trim();
            if (cleanName.isEmpty())
                continue;

            // Bỏ qua nếu trùng captain
            if (cleanName.equalsIgnoreCase(request.captainDiscord().trim()))
                continue;

            Member member = findMemberByUsername(guild, cleanName);
            if (member == null) {
                notFound.add(cleanName);
            } else if (!newMembers.contains(member)) {
                newMembers.add(member);
            }
        }

        if (!notFound.isEmpty()) {
            return ConfirmTeamResponse.error("Không tìm thấy các thành viên sau trong server: "
                    + String.join(", ", notFound)
                    + ". Kiểm tra lại Discord username.");
        }

        // ─── Kiểm tra channel đã tồn tại → CREATE vs UPDATE ───
        String textChannelName = "💬-chat-" + request.shortName();
        String voiceChannelName = "🔊-onlan-" + request.shortName();

        TextChannel existingTextChannel = findTextChannelByName(guild, textChannelName);

        if (existingTextChannel != null) {
            return handleUpdate(guild, existingTextChannel, request, newMembers, captainMember,
                    roleTuyenThu, roleDoiTruong, roleAdmin, voiceChannelName);
        } else {
            return handleCreate(guild, teamCategory, request, newMembers, captainMember,
                    roleTuyenThu, roleDoiTruong, roleAdmin, textChannelName, voiceChannelName);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // CREATE FLOW
    // ════════════════════════════════════════════════════════════════

    private ConfirmTeamResponse handleCreate(Guild guild, Category category,
            ConfirmTeamRequest request, List<Member> members, Member captain,
            Role roleTuyenThu, Role roleDoiTruong, Role roleAdmin,
            String textChannelName, String voiceChannelName) {

        logger.info("CREATE team '{}' ({}) with {} members.",
                request.teamName(), request.shortName(), members.size());

        // Gán roles
        assignRoles(guild, members, captain, roleTuyenThu, roleDoiTruong);

        // Tạo text channel
        try {
            TextChannel textChannel = category.createTextChannel(textChannelName)
                    .addPermissionOverride(guild.getPublicRole(),
                            null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(roleAdmin,
                            EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .complete();

            // Thêm permission cho members
            for (Member member : members) {
                textChannel.upsertPermissionOverride(member)
                        .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL))
                        .complete();
            }

            // Gửi welcome message
            sendWelcomeMessage(textChannel, request.teamName(), captain);

            logger.info("Created text channel '{}' for team '{}'.", textChannelName, request.teamName());
        } catch (Exception e) {
            logger.error("Failed to create text channel for team '{}': {}", request.teamName(), e.getMessage(), e);
            return ConfirmTeamResponse.error("Lỗi tạo kênh text: " + e.getMessage());
        }

        // Tạo voice channel
        try {
            VoiceChannel voiceChannel = category.createVoiceChannel(voiceChannelName)
                    .addPermissionOverride(guild.getPublicRole(),
                            null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(roleAdmin,
                            EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .complete();

            for (Member member : members) {
                voiceChannel.upsertPermissionOverride(member)
                        .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL))
                        .complete();
            }

            logger.info("Created voice channel '{}' for team '{}'.", voiceChannelName, request.teamName());
        } catch (Exception e) {
            logger.error("Failed to create voice channel for team '{}': {}", request.teamName(), e.getMessage(), e);
            return ConfirmTeamResponse.error("Lỗi tạo kênh voice: " + e.getMessage());
        }

        String memberNames = members.stream()
                .map(m -> m.getUser().getName())
                .collect(Collectors.joining(", "));

        return ConfirmTeamResponse.created(
                String.format("Team '%s' đã được tạo thành công! Captain: %s, Members: %s",
                        request.teamName(), captain.getUser().getName(), memberNames));
    }

    // ════════════════════════════════════════════════════════════════
    // UPDATE FLOW
    // ════════════════════════════════════════════════════════════════

    private ConfirmTeamResponse handleUpdate(Guild guild, TextChannel existingTextChannel,
            ConfirmTeamRequest request, List<Member> newMembers, Member newCaptain,
            Role roleTuyenThu, Role roleDoiTruong, Role roleAdmin,
            String voiceChannelName) {

        logger.info("UPDATE team '{}' ({}).", request.teamName(), request.shortName());

        // Lấy danh sách member cũ từ permission overrides
        Set<Member> oldMembers = new HashSet<>();
        existingTextChannel.getMemberPermissionOverrides().forEach(override -> {
            Member m = override.getMember();
            if (m != null && !m.getUser().isBot()) {
                oldMembers.add(m);
            }
        });

        Set<Member> newMemberSet = new HashSet<>(newMembers);

        // Diff: members cần xóa vs members cần thêm
        Set<Member> toRemove = new HashSet<>(oldMembers);
        toRemove.removeAll(newMemberSet);

        Set<Member> toAdd = new HashSet<>(newMemberSet);
        toAdd.removeAll(oldMembers);

        // ─── Xử lý members bị XÓA ───
        for (Member removed : toRemove) {
            logger.info("Removing member '{}' from team '{}'.", removed.getUser().getName(), request.teamName());

            // Thu hồi roles
            guild.removeRoleFromMember(removed, roleTuyenThu).queue(
                    s -> logger.info("Revoked Tuyển Thủ from {}", removed.getUser().getName()),
                    e -> logger.error("Failed to revoke Tuyển Thủ from {}: {}",
                            removed.getUser().getName(), e.getMessage()));

            guild.removeRoleFromMember(removed, roleDoiTruong).queue(
                    s -> logger.info("Revoked Đội Trưởng from {}", removed.getUser().getName()),
                    e -> logger.error("Failed to revoke Đội Trưởng from {}: {}",
                            removed.getUser().getName(), e.getMessage()));

            // Xóa permission override khỏi text channel
            existingTextChannel.upsertPermissionOverride(removed)
                    .clear(Permission.VIEW_CHANNEL)
                    .setDenied(EnumSet.of(Permission.VIEW_CHANNEL))
                    .queue();
        }

        // ─── Xử lý members được THÊM ───
        for (Member added : toAdd) {
            logger.info("Adding member '{}' to team '{}'.", added.getUser().getName(), request.teamName());

            // Gán role Tuyển Thủ
            guild.addRoleToMember(added, roleTuyenThu).queue(
                    s -> logger.info("Assigned Tuyển Thủ to {}", added.getUser().getName()),
                    e -> logger.error("Failed to assign Tuyển Thủ to {}: {}",
                            added.getUser().getName(), e.getMessage()));

            // Thêm permission vào text channel
            existingTextChannel.upsertPermissionOverride(added)
                    .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL))
                    .queue();
        }

        // ─── Cập nhật captain ───
        // Thu hồi role Đội Trưởng từ tất cả members cũ (trừ captain mới)
        for (Member oldMember : oldMembers) {
            if (!oldMember.equals(newCaptain) && oldMember.getRoles().contains(roleDoiTruong)) {
                guild.removeRoleFromMember(oldMember, roleDoiTruong).queue(
                        s -> logger.info("Revoked Đội Trưởng from old captain {}", oldMember.getUser().getName()),
                        e -> logger.error("Failed to revoke Đội Trưởng: {}", e.getMessage()));
            }
        }
        // Gán role Đội Trưởng cho captain mới
        guild.addRoleToMember(newCaptain, roleDoiTruong).queue(
                s -> logger.info("Assigned Đội Trưởng to new captain {}", newCaptain.getUser().getName()),
                e -> logger.error("Failed to assign Đội Trưởng: {}", e.getMessage()));

        // Gán role Tuyển Thủ cho captain mới (nếu chưa có)
        guild.addRoleToMember(newCaptain, roleTuyenThu).queue();

        // ─── Cập nhật voice channel permissions ───
        VoiceChannel existingVoiceChannel = findVoiceChannelByName(guild, voiceChannelName);
        if (existingVoiceChannel != null) {
            // Xóa members cũ
            for (Member removed : toRemove) {
                existingVoiceChannel.upsertPermissionOverride(removed)
                        .clear(Permission.VIEW_CHANNEL)
                        .setDenied(EnumSet.of(Permission.VIEW_CHANNEL))
                        .queue();
            }
            // Thêm members mới
            for (Member added : toAdd) {
                existingVoiceChannel.upsertPermissionOverride(added)
                        .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL))
                        .queue();
            }
        }

        // ─── Gửi thông báo cập nhật ───
        StringBuilder updateMsg = new StringBuilder();
        updateMsg.append("🔄 **ĐỘI ĐÃ ĐƯỢC CẬP NHẬT BỞI ADMIN**\n\n");

        if (!toRemove.isEmpty()) {
            updateMsg.append("❌ **Thành viên bị xóa:** ");
            updateMsg.append(toRemove.stream()
                    .map(m -> m.getUser().getName())
                    .collect(Collectors.joining(", ")));
            updateMsg.append("\n");
        }

        if (!toAdd.isEmpty()) {
            updateMsg.append("✅ **Thành viên mới:** ");
            updateMsg.append(toAdd.stream()
                    .map(Member::getAsMention)
                    .collect(Collectors.joining(", ")));
            updateMsg.append("\n");
        }

        updateMsg.append("\n👑 **Đội trưởng:** ").append(newCaptain.getAsMention());
        updateMsg.append("\n👥 **Tổng thành viên:** ").append(newMembers.size());

        existingTextChannel.sendMessage(updateMsg.toString()).queue();

        String summary = String.format("Team '%s' đã được cập nhật. Xóa %d, thêm %d thành viên.",
                request.teamName(), toRemove.size(), toAdd.size());

        return ConfirmTeamResponse.updated(summary);
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Gán role Tuyển Thủ cho tất cả members, Đội Trưởng cho captain.
     */
    private void assignRoles(Guild guild, List<Member> members, Member captain,
            Role roleTuyenThu, Role roleDoiTruong) {
        for (Member member : members) {
            guild.addRoleToMember(member, roleTuyenThu).queue(
                    s -> logger.info("Assigned Tuyển Thủ to {}", member.getUser().getName()),
                    e -> logger.error("Failed to assign Tuyển Thủ to {}: {}",
                            member.getUser().getName(), e.getMessage()));
        }

        guild.addRoleToMember(captain, roleDoiTruong).queue(
                s -> logger.info("Assigned Đội Trưởng to {}", captain.getUser().getName()),
                e -> logger.error("Failed to assign Đội Trưởng to {}: {}",
                        captain.getUser().getName(), e.getMessage()));
    }

    /**
     * Gửi tin nhắn chào mừng vào kênh text mới.
     */
    private void sendWelcomeMessage(TextChannel channel, String teamName, Member captain) {
        String welcomeMsg = String.format(
                "🔥 **CHÀO MỪNG TEAM %s ĐÃ ĐẾN VỚI UNICORN CHAMPIONSHIP!** 🔥\n\n"
                        + "Đội của bạn đã được duyệt thành công. Đội trưởng %s hãy đại diện team qua channel <#%s> "
                        + "để thực hiện các bước cuối cùng:\n"
                        + "1️⃣ Tag đầy đủ Discord 5-6 thành viên.\n"
                        + "2️⃣ Ghi rõ Tên Đội.\n"
                        + "3️⃣ Gửi ảnh Team/Logo Team để Admin check lần cuối.\n\n"
                        + "Chúc anh em có những trận đấu sấy rát tay tại Unicorn! 🚀",
                teamName, captain.getAsMention(), ID_CHANNEL_CHECKIN);

        channel.sendMessage(welcomeMsg).queue(
                s -> logger.info("Welcome message sent to channel {}", channel.getName()),
                e -> logger.error("Failed to send welcome message: {}", e.getMessage()));
    }

    /**
     * Tìm member bằng Discord username (case-insensitive).
     * Hỗ trợ: username thuần (kheng.joke), @username, hoặc tên hiển thị.
     */
    private Member findMemberByUsername(Guild guild, String username) {
        if (username == null || username.isBlank())
            return null;

        String cleanName = username.trim();
        if (cleanName.startsWith("@")) {
            cleanName = cleanName.substring(1);
        }

        // Tìm bằng username (new Discord username system — kheng.joke)
        List<Member> found = guild.getMembersByName(cleanName, true);
        if (!found.isEmpty())
            return found.get(0);

        // Tìm bằng effective name (nickname/display name)
        found = guild.getMembersByEffectiveName(cleanName, true);
        if (!found.isEmpty())
            return found.get(0);

        // Tìm bằng tag cũ (nếu có #)
        if (cleanName.contains("#")) {
            String nameOnly = cleanName.split("#")[0];
            found = guild.getMembersByName(nameOnly, true);
            if (!found.isEmpty())
                return found.get(0);
        }

        return null;
    }

    /**
     * Tìm text channel trong guild theo tên (case-insensitive).
     */
    private TextChannel findTextChannelByName(Guild guild, String name) {
        return guild.getTextChannels().stream()
                .filter(ch -> ch.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Tìm voice channel trong guild theo tên (case-insensitive).
     */
    private VoiceChannel findVoiceChannelByName(Guild guild, String name) {
        return guild.getVoiceChannels().stream()
                .filter(ch -> ch.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
