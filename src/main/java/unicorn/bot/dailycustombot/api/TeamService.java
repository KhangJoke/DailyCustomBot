package unicorn.bot.dailycustombot.api;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.EnvLoader;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TeamService {
    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    private static final String ID_ROLE_TUYEN_THU = "1493680420498444449";
    private static final String ID_ROLE_DOI_TRUONG = "1493680761142907022";
    private static final String ID_ROLE_ADMIN = "1477129745543204935";
    private static final String ID_CHANNEL_CHECKIN = "1493672233464234146";
    private static final String ID_CATEGORY_TEAMS = "1461604155687702743";

    private final JDA jda;

    public TeamService(JDA jda) {
        this.jda = jda;
    }

    // ==========================================
    // ENTRY POINT — Phân loại action từ Sheet
    // ==========================================
    public ConfirmTeamResponse confirmTeam(ConfirmTeamRequest request) {
        Guild guild = jda.getGuilds().isEmpty() ? null : jda.getGuilds().get(0);
        if (guild == null)
            return ConfirmTeamResponse.error("Bot chưa vào server!");

        // ─── NEW / EDIT — Thông báo cho Admin ───
        if ("NEW".equalsIgnoreCase(request.action()) || "EDIT".equalsIgnoreCase(request.action())) {
            boolean isEdit = "EDIT".equalsIgnoreCase(request.action());
            return handleNotify(guild, request, isEdit);
        }

        Role roleTuyenThu = guild.getRoleById(ID_ROLE_TUYEN_THU);
        Role roleDoiTruong = guild.getRoleById(ID_ROLE_DOI_TRUONG);
        Role roleAdmin = guild.getRoleById(ID_ROLE_ADMIN);
        Category teamCategory = guild.getCategoryById(ID_CATEGORY_TEAMS);

        if (roleTuyenThu == null || roleDoiTruong == null || teamCategory == null) {
            return ConfirmTeamResponse.error("Lỗi ID Role/Category!");
        }

        // ─── CANCEL ───
        if ("CANCEL".equalsIgnoreCase(request.action())) {
            return handleCancel(guild, request, roleTuyenThu, roleDoiTruong);
        }

        // ─── CONFIRM (CREATE hoặc UPDATE) ───
        Member captainMember = findMemberByUsername(guild, request.captainDiscord());
        if (captainMember == null)
            return ConfirmTeamResponse.error("Không tìm thấy Captain: " + request.captainDiscord());

        Set<Member> finalMembers = new LinkedHashSet<>();
        finalMembers.add(captainMember);
        if (request.membersDiscord() != null) {
            for (String username : request.membersDiscord()) {
                if (username == null || username.isBlank())
                    continue;
                Member m = findMemberByUsername(guild, username.trim());
                if (m != null)
                    finalMembers.add(m);
            }
        }

        List<Member> memberList = new ArrayList<>(finalMembers);
        String textName = "chat-" + request.shortName().toLowerCase().replace(" ", "-");
        String voiceName = "voice-" + request.shortName().toLowerCase().replace(" ", "-");

        TextChannel existingText = findTextChannelByName(guild, textName);

        if (existingText != null) {
            return handleUpdate(guild, existingText, request, memberList, captainMember, roleTuyenThu, roleDoiTruong,
                    roleAdmin, voiceName);
        } else {
            return handleCreate(guild, teamCategory, request, memberList, captainMember, roleTuyenThu, roleDoiTruong,
                    roleAdmin, textName, voiceName);
        }
    }

    // ==========================================
    // NEW / EDIT — Thông báo đăng ký mới hoặc chỉnh sửa form cho Admin
    // ==========================================
    private ConfirmTeamResponse handleNotify(Guild guild, ConfirmTeamRequest request, boolean isEdit) {
        try {
            // Đọc channel ID thông báo admin từ env var, fallback dùng channel check-in
            String adminChannelId = EnvLoader.get("ADMIN_NOTIFY_CHANNEL_ID");
            if (adminChannelId == null || adminChannelId.isBlank()) {
                adminChannelId = ID_CHANNEL_CHECKIN;
                logger.warn("ADMIN_NOTIFY_CHANNEL_ID chưa cấu hình, dùng channel check-in mặc định.");
            }

            TextChannel adminChannel = guild.getTextChannelById(adminChannelId);
            if (adminChannel == null) {
                return ConfirmTeamResponse.error("Không tìm thấy channel thông báo admin (ID: " + adminChannelId + ")");
            }

            // Tạo link trực tiếp đến sheet
            String sheetLink = request.sheetUrl() != null && !request.sheetUrl().isBlank()
                    ? request.sheetUrl()
                    : "Không có link";

            String sheetName = request.sheetName() != null && !request.sheetName().isBlank()
                    ? request.sheetName()
                    : "Sheet";

            String teamName = request.teamName() != null ? request.teamName() : "Chưa rõ";
            String captain = request.captainDiscord() != null ? request.captainDiscord() : "Chưa rõ";

            // Tạo Embed — phân biệt NEW vs EDIT
            String title = isEdit ? "✏️ ĐỘI CHỈNH SỬA THÔNG TIN!" : "📋 ĐƠN ĐĂNG KÝ MỚI!";
            Color embedColor = isEdit ? new Color(230, 126, 34) : new Color(52, 152, 219); // Cam cho EDIT, Xanh cho NEW

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setColor(embedColor)
                    .addField("🏷️ Tên đội", teamName, true)
                    .addField("👑 Đội trưởng", captain, true)
                    .addField("📄 Sheet", sheetName, true)
                    .addField("📍 Dòng", String.valueOf(request.rowNumber()), true)
                    .addField("🔗 Link Sheet", "[👉 Mở Google Sheet](" + sheetLink + ")", false)
                    .setTimestamp(Instant.now());

            if (isEdit) {
                embed.setFooter("Đội đã chỉnh sửa form. Kiểm tra lại thông tin trên Sheet.");
            } else {
                embed.setFooter("Vui lòng vào Sheet → chuyển Status thành \"Confirm\" để duyệt đội.");
            }

            // Tag role Admin để thông báo
            Role roleAdmin = guild.getRoleById(ID_ROLE_ADMIN);
            String mentionText;
            if (isEdit) {
                mentionText = roleAdmin != null
                        ? roleAdmin.getAsMention() + " — Đội **" + teamName + "** vừa chỉnh sửa thông tin đăng ký!"
                        : "⚠️ Đội **" + teamName + "** vừa chỉnh sửa thông tin đăng ký!";
            } else {
                mentionText = roleAdmin != null
                        ? roleAdmin.getAsMention() + " — Có đội mới đăng ký, cần duyệt!"
                        : "⚠️ Có đội mới đăng ký, cần duyệt!";
            }

            adminChannel.sendMessage(mentionText)
                    .setEmbeds(embed.build())
                    .queue();

            String logAction = isEdit ? "chỉnh sửa" : "đăng ký mới";
            logger.info("Đã gửi thông báo {}: team='{}', row={}", logAction, teamName, request.rowNumber());
            return ConfirmTeamResponse.notified("Đã thông báo cho Admin về đội " + teamName);
        } catch (Exception e) {
            logger.error("Lỗi khi gửi thông báo: {}", e.getMessage(), e);
            return ConfirmTeamResponse.error("Lỗi gửi thông báo: " + e.getMessage());
        }
    }

    // ==========================================
    // CREATE — Tạo mới đội (Role + Channel)
    // ==========================================
    private ConfirmTeamResponse handleCreate(Guild guild, Category category, ConfirmTeamRequest request,
            List<Member> members, Member captain, Role roleTuyenThu, Role roleDoiTruong, Role roleAdmin,
            String textName, String voiceName) {
        try {
            TextChannel txt = category.createTextChannel(textName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(roleAdmin, EnumSet.of(Permission.VIEW_CHANNEL), null).complete();

            VoiceChannel vc = category.createVoiceChannel(voiceName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(roleAdmin, EnumSet.of(Permission.VIEW_CHANNEL), null).complete();

            // Áp dụng quyền và role cho toàn đội
            for (Member m : members) {
                guild.addRoleToMember(m, roleTuyenThu).queue();
                txt.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
                vc.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
            }
            guild.addRoleToMember(captain, roleDoiTruong).queue();

            sendWelcomeMessage(txt, request.teamName(), captain, members);
            return ConfirmTeamResponse.created("Đã tạo " + request.teamName());
        } catch (Exception e) {
            return ConfirmTeamResponse.error(e.getMessage());
        }
    }

    // ==========================================
    // UPDATE — Cập nhật đội hình (sync Role + quyền channel)
    // ==========================================
    private ConfirmTeamResponse handleUpdate(Guild guild, TextChannel textChannel, ConfirmTeamRequest request,
            List<Member> newMembers, Member captain, Role roleTuyenThu, Role roleDoiTruong, Role roleAdmin,
            String voiceName) {
        VoiceChannel voiceChannel = findVoiceChannelByName(guild, voiceName);

        // 1. LẤY TẤT CẢ ID ĐANG CÓ TRONG ROOM (Dữ liệu cũ)
        Set<String> oldMemberIds = textChannel.getPermissionOverrides().stream()
                .filter(ov -> ov.isMemberOverride()).map(ov -> ov.getId()).collect(Collectors.toSet());

        Set<String> currentMemberIds = newMembers.stream().map(Member::getId).collect(Collectors.toSet());

        // 2. XÓA SẠCH NHỮNG AI KHÔNG CÒN TRONG ĐỘI
        for (String id : oldMemberIds) {
            if (!currentMemberIds.contains(id)) {
                // 2.1 Xóa Role (Dùng Snowflake để lột role không cần Cache)
                UserSnowflake user = UserSnowflake.fromId(id);
                guild.removeRoleFromMember(user, roleTuyenThu).queue();
                guild.removeRoleFromMember(user, roleDoiTruong).queue();

                // 2.2 Ép xóa quyền Room (Dùng retrieveMemberById để né hoàn toàn lỗi Cache)
                guild.retrieveMemberById(id).queue(
                        memberToRemove -> {
                            // Nếu bắt được người -> Ép quyền DENY (Cấm cửa 100%)
                            textChannel.upsertPermissionOverride(memberToRemove)
                                    .clear(Permission.VIEW_CHANNEL)
                                    .setDenied(EnumSet.of(Permission.VIEW_CHANNEL)).queue();

                            if (voiceChannel != null) {
                                voiceChannel.upsertPermissionOverride(memberToRemove)
                                        .clear(Permission.VIEW_CHANNEL)
                                        .setDenied(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
                            }
                        },
                        error -> {
                            // Nếu user đã out server (Discord trả lỗi) -> Dọn dẹp override rác bằng ID
                            // thuần
                            textChannel.getManager().removePermissionOverride(Long.parseLong(id)).queue();
                            if (voiceChannel != null) {
                                voiceChannel.getManager().removePermissionOverride(Long.parseLong(id)).queue();
                            }
                        });
            }
        }

        // 3. ÉP SET LẠI ROLE VÀ QUYỀN CHO TOÀN BỘ ĐỘI HÌNH HIỆN TẠI (Đảm bảo 100% không
        // sót)
        for (Member m : newMembers) {
            guild.addRoleToMember(m, roleTuyenThu).queue();
            textChannel.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
            if (voiceChannel != null)
                voiceChannel.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
        }

        // 4. CẬP NHẬT ROLE ĐỘI TRƯỞNG
        for (String id : oldMemberIds) {
            if (!id.equals(captain.getId()))
                guild.removeRoleFromMember(UserSnowflake.fromId(id), roleDoiTruong).queue();
        }
        guild.addRoleToMember(captain, roleDoiTruong).queue();

        // 5. THÔNG BÁO GỌN GÀNG
        String mentions = newMembers.stream().map(Member::getAsMention).collect(Collectors.joining(" "));
        StringBuilder sb = new StringBuilder("🔄 **ĐỘI ĐÃ ĐƯỢC CẬP NHẬT**\n\n");
        sb.append("👑 **Đội trưởng:** ").append(captain.getAsMention()).append("\n");
        sb.append("👥 **Thành viên:** ").append(mentions);

        textChannel.sendMessage(sb.toString()).queue();
        return ConfirmTeamResponse.updated("Cập nhật " + request.teamName());
    }

    // ==========================================
    // CANCEL — Hủy đội (xóa Role + xóa Channel)
    // ==========================================
    private ConfirmTeamResponse handleCancel(Guild guild, ConfirmTeamRequest request,
            Role roleTuyenThu, Role roleDoiTruong) {
        try {
            String textName = "chat-" + request.shortName().toLowerCase().replace(" ", "-");
            String voiceName = "voice-" + request.shortName().toLowerCase().replace(" ", "-");

            TextChannel textChannel = findTextChannelByName(guild, textName);
            VoiceChannel voiceChannel = findVoiceChannelByName(guild, voiceName);

            if (textChannel == null && voiceChannel == null) {
                return ConfirmTeamResponse.cancelled("Đội " + request.teamName() + " chưa có channel, không cần hủy.");
            }

            // 1. LỘT ROLE CỦA TẤT CẢ THÀNH VIÊN ĐANG CÓ TRONG CHANNEL
            if (textChannel != null) {
                Set<String> memberIds = textChannel.getPermissionOverrides().stream()
                        .filter(ov -> ov.isMemberOverride())
                        .map(ov -> ov.getId())
                        .collect(Collectors.toSet());

                for (String id : memberIds) {
                    UserSnowflake user = UserSnowflake.fromId(id);
                    guild.removeRoleFromMember(user, roleTuyenThu).queue();
                    guild.removeRoleFromMember(user, roleDoiTruong).queue();
                }

                // 2. GỬI THÔNG BÁO TRƯỚC KHI XÓA
                textChannel.sendMessage("⚠️ **ĐỘI " + request.teamName().toUpperCase()
                        + " ĐÃ BỊ HỦY!** Channel sẽ bị xóa trong giây lát...").complete();

                // 3. XÓA TEXT CHANNEL
                textChannel.delete().reason("Hủy đội " + request.teamName()).queue();
                logger.info("Đã xóa text channel: {}", textName);
            }

            // 4. XÓA VOICE CHANNEL
            if (voiceChannel != null) {
                voiceChannel.delete().reason("Hủy đội " + request.teamName()).queue();
                logger.info("Đã xóa voice channel: {}", voiceName);
            }

            return ConfirmTeamResponse.cancelled("Đã hủy đội " + request.teamName() + " — xóa Role & Channel.");
        } catch (Exception e) {
            logger.error("Lỗi khi hủy đội {}: {}", request.teamName(), e.getMessage(), e);
            return ConfirmTeamResponse.error("Lỗi khi hủy: " + e.getMessage());
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private void sendWelcomeMessage(TextChannel channel, String teamName, Member captain, List<Member> members) {
        String mentions = members.stream().map(Member::getAsMention).collect(Collectors.joining(" "));
        channel.sendMessage(
                String.format("🔥 **TEAM %s ĐÃ ĐƯỢC DUYỆT!** 🔥\n\nThành viên: %s\n\nĐội trưởng %s check-in tại <#%s>!",
                        teamName, mentions, captain.getAsMention(), ID_CHANNEL_CHECKIN))
                .queue();
    }

    private Member findMemberByUsername(Guild guild, String username) {
        if (username == null || username.isBlank())
            return null;
        String clean = username.trim().toLowerCase().replace("@", "");
        List<Member> cached = guild.getMembersByName(clean, true);
        if (!cached.isEmpty())
            return cached.get(0);
        cached = guild.getMembersByEffectiveName(clean, true);
        if (!cached.isEmpty())
            return cached.get(0);
        try {
            List<Member> fetched = guild.retrieveMembersByPrefix(clean, 10).get();
            for (Member m : fetched)
                if (m.getUser().getName().equalsIgnoreCase(clean) || m.getEffectiveName().equalsIgnoreCase(clean))
                    return m;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    private TextChannel findTextChannelByName(Guild guild, String name) {
        return guild.getTextChannels().stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst()
                .orElse(null);
    }

    private VoiceChannel findVoiceChannelByName(Guild guild, String name) {
        return guild.getVoiceChannels().stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst()
                .orElse(null);
    }
}