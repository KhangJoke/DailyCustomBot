package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler cho lệnh /confirm-team.
 * Xác nhận đội thi đấu cho UNICORN CHAMPIONSHIP:
 * - Gán role "Tuyển Thủ" và "Đội Trưởng" cho các thành viên.
 * - Tạo kênh Text và Voice riêng cho đội.
 * - Gửi tin nhắn chào mừng vào kênh đội.
 */
public class ConfirmTeamCommand {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmTeamCommand.class);

    // ============================================================
    // CẤU HÌNH - Thay đổi các ID này cho phù hợp với server của bạn
    // ============================================================
    private static final String ID_ROLE_TUYEN_THU = "1493680420498444449"; // Role "Tuyển Thủ"
    private static final String ID_ROLE_DOI_TRUONG = "1493680761142907022"; // Role "Đội Trưởng"
    private static final String ID_ROLE_ADMIN = "1477129745543204935"; // Role "Admin" (có quyền xem kênh đội)
    private static final String ID_CHANNEL_CHECKIN = "1493684466311364690"; // Channel check-in
    private static final String ID_CATEGORY_TEAMS = "1461604155687702743"; // Category chứa kênh đội

    // Pattern để nhận diện Discord mention (<@123456789>) hoặc username thuần
    private static final Pattern MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");

    public void handle(SlashCommandInteractionEvent event) {
        // Chỉ Admin mới được sử dụng
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn cần quyền **Administrator** để sử dụng lệnh này!")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ Lệnh này chỉ sử dụng được trong server!").setEphemeral(true).queue();
            return;
        }

        // Defer reply vì xử lý có thể mất vài giây
        event.deferReply(true).queue();

        // ─── Lấy tham số ───
        String teamName = event.getOption("team_name", OptionMapping::getAsString);
        String shortName = event.getOption("short_name", OptionMapping::getAsString);
        User captainUser = event.getOption("captain", OptionMapping::getAsUser);
        String membersRaw = event.getOption("members", OptionMapping::getAsString);

        if (teamName == null || shortName == null || captainUser == null || membersRaw == null) {
            event.getHook().sendMessage("❌ Thiếu tham số bắt buộc! Vui lòng điền đầy đủ.").queue();
            return;
        }

        // ─── Validate roles tồn tại ───
        Role roleTuyenThu = guild.getRoleById(ID_ROLE_TUYEN_THU);
        Role roleDoiTruong = guild.getRoleById(ID_ROLE_DOI_TRUONG);
        Role roleAdmin = guild.getRoleById(ID_ROLE_ADMIN);

        if (roleTuyenThu == null) {
            event.getHook()
                    .sendMessage("❌ Không tìm thấy Role **Tuyển Thủ** (ID: `" + ID_ROLE_TUYEN_THU
                            + "`). Kiểm tra lại config!")
                    .queue();
            return;
        }
        if (roleDoiTruong == null) {
            event.getHook()
                    .sendMessage("❌ Không tìm thấy Role **Đội Trưởng** (ID: `" + ID_ROLE_DOI_TRUONG
                            + "`). Kiểm tra lại config!")
                    .queue();
            return;
        }
        if (roleAdmin == null) {
            event.getHook()
                    .sendMessage("❌ Không tìm thấy Role **Admin** (ID: `" + ID_ROLE_ADMIN + "`). Kiểm tra lại config!")
                    .queue();
            return;
        }

        // ─── Validate category tồn tại ───
        Category teamCategory = guild.getCategoryById(ID_CATEGORY_TEAMS);
        if (teamCategory == null) {
            event.getHook()
                    .sendMessage("❌ Không tìm thấy Category cho team (ID: `" + ID_CATEGORY_TEAMS
                            + "`). Kiểm tra lại config!")
                    .queue();
            return;
        }

        // ─── Xử lý danh sách thành viên ───
        List<Member> teamMembers = new ArrayList<>();
        List<String> notFoundUsers = new ArrayList<>();

        // Thêm captain trước
        Member captainMember = guild.getMember(captainUser);
        if (captainMember == null) {
            event.getHook().sendMessage("❌ Không tìm thấy đội trưởng **" + captainUser.getName() + "** trong server!")
                    .queue();
            return;
        }
        teamMembers.add(captainMember);

        // Parse members string: hỗ trợ mention (<@ID>), username, phân cách bằng
        // space/comma
        String[] memberTokens = membersRaw.split("[,\\s]+");
        for (String token : memberTokens) {
            token = token.trim();
            if (token.isEmpty())
                continue;

            Member member = null;

            // Thử match mention pattern
            Matcher matcher = MENTION_PATTERN.matcher(token);
            if (matcher.matches()) {
                String userId = matcher.group(1);
                member = guild.getMemberById(userId);
            } else {
                // Thử tìm bằng username (không phân biệt hoa thường)
                String searchName = token.startsWith("@") ? token.substring(1) : token;
                List<Member> found = guild.getMembersByName(searchName, true);
                if (found.isEmpty()) {
                    // Thử tìm theo effective name (nickname)
                    found = guild.getMembersByEffectiveName(searchName, true);
                }
                if (!found.isEmpty()) {
                    member = found.get(0);
                }
            }

            if (member == null) {
                notFoundUsers.add(token);
            } else if (!teamMembers.contains(member)) {
                // Tránh thêm trùng (VD: captain cũng nằm trong members string)
                teamMembers.add(member);
            }
        }

        if (!notFoundUsers.isEmpty()) {
            event.getHook().sendMessage("⚠️ Không tìm thấy các thành viên sau trong server: **"
                    + String.join(", ", notFoundUsers) + "**\n"
                    + "Hãy kiểm tra lại username hoặc đảm bảo họ đã join server.")
                    .queue();
            return;
        }

        // ─── Gán roles ───
        for (Member member : teamMembers) {
            guild.addRoleToMember(member, roleTuyenThu).queue(
                    success -> logger.info("Assigned Tuyển Thủ to {}", member.getUser().getName()),
                    error -> logger.error("Failed to assign Tuyển Thủ to {}: {}",
                            member.getUser().getName(), error.getMessage()));
        }

        // Gán role Đội Trưởng riêng cho captain
        guild.addRoleToMember(captainMember, roleDoiTruong).queue(
                success -> logger.info("Assigned Đội Trưởng to {}", captainMember.getUser().getName()),
                error -> logger.error("Failed to assign Đội Trưởng to {}: {}",
                        captainMember.getUser().getName(), error.getMessage()));

        // ─── Tạo kênh riêng cho đội ───
        String textChannelName = "💬-chat-" + shortName;
        String voiceChannelName = "🔊-onlan-" + shortName;

        // Tạo text channel
        teamCategory.createTextChannel(textChannelName)
                .addPermissionOverride(guild.getPublicRole(),
                        null, EnumSet.of(Permission.VIEW_CHANNEL)) // @everyone: deny VIEW
                .addPermissionOverride(roleAdmin,
                        EnumSet.of(Permission.VIEW_CHANNEL), null) // Admin: allow VIEW
                .queue(textChannel -> {
                    // Thêm permission cho từng thành viên
                    for (Member member : teamMembers) {
                        textChannel.upsertPermissionOverride(member)
                                .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL))
                                .queue();
                    }

                    // Gửi tin nhắn chào mừng
                    sendWelcomeMessage(textChannel, teamName, captainMember);

                    logger.info("Created text channel {} for team {}", textChannelName, teamName);
                }, error -> {
                    logger.error("Failed to create text channel for team {}: {}", teamName, error.getMessage());
                    event.getHook().sendMessage("❌ Lỗi khi tạo kênh text: " + error.getMessage()).queue();
                });

        // Tạo voice channel
        teamCategory.createVoiceChannel(voiceChannelName)
                .addPermissionOverride(guild.getPublicRole(),
                        null, EnumSet.of(Permission.VIEW_CHANNEL)) // @everyone: deny VIEW
                .addPermissionOverride(roleAdmin,
                        EnumSet.of(Permission.VIEW_CHANNEL), null) // Admin: allow VIEW
                .queue(voiceChannel -> {
                    // Thêm permission cho từng thành viên
                    for (Member member : teamMembers) {
                        voiceChannel.upsertPermissionOverride(member)
                                .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL))
                                .queue();
                    }

                    logger.info("Created voice channel {} for team {}", voiceChannelName, teamName);
                }, error -> {
                    logger.error("Failed to create voice channel for team {}: {}", teamName, error.getMessage());
                    event.getHook().sendMessage("❌ Lỗi khi tạo kênh voice: " + error.getMessage()).queue();
                });

        // ─── Phản hồi cho Admin ───
        StringBuilder membersList = new StringBuilder();
        for (Member m : teamMembers) {
            String label = m.equals(captainMember) ? " 👑" : "";
            membersList.append("• ").append(m.getAsMention()).append(label).append("\n");
        }

        String confirmMessage = String.format("""
                ✅ **Team "%s" đã được thiết lập thành công!**

                📋 **Thông tin đội:**
                • **Tên đội:** %s
                • **Tên viết tắt:** %s
                • **Đội trưởng:** %s
                • **Số thành viên:** %d

                👥 **Danh sách thành viên:**
                %s
                🏠 **Kênh đội:**
                • 💬 `%s`
                • 🔊 `%s`

                🎯 Role **Tuyển Thủ** và **Đội Trưởng** đã được gán tự động.
                """,
                teamName, teamName, shortName,
                captainMember.getAsMention(),
                teamMembers.size(),
                membersList.toString(),
                textChannelName, voiceChannelName);

        event.getHook().sendMessage(confirmMessage).queue();
        logger.info("Team '{}' ({}) confirmed by admin {} with {} members.",
                teamName, shortName, event.getUser().getName(), teamMembers.size());
    }

    /**
     * Gửi tin nhắn chào mừng vào kênh text riêng của đội.
     */
    private void sendWelcomeMessage(TextChannel channel, String teamName, Member captain) {
        String welcomeMsg = String.format(
                """
                        🔥 **CHÀO MỪNG TEAM %s ĐÃ ĐẾN VỚI UNICORN CHAMPIONSHIP!** 🔥

                        Đội của bạn đã được duyệt thành công. Đội trưởng %s hãy đại diện team qua channel <#%s> để thực hiện các bước cuối cùng:
                        1️⃣ Tag đầy đủ Discord 5-6 thành viên.
                        2️⃣ Ghi rõ Tên Đội.
                        3️⃣ Gửi ảnh Team/Logo Team để Admin check lần cuối.

                        Chúc anh em có những trận đấu sấy rát tay tại Unicorn! 🚀
                        """,
                teamName,
                captain.getAsMention(),
                ID_CHANNEL_CHECKIN);

        channel.sendMessage(welcomeMsg).queue(
                success -> logger.info("Welcome message sent to channel {}", channel.getName()),
                error -> logger.error("Failed to send welcome message to {}: {}", channel.getName(),
                        error.getMessage()));
    }
}
