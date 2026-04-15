package unicorn.bot.dailycustombot.api;

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

import java.util.*;
import java.util.stream.Collectors;

public class TeamService {
    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    // CẤU HÌNH ID
    private static final String ID_ROLE_TUYEN_THU = "1493680420498444449";
    private static final String ID_ROLE_DOI_TRUONG = "1493680761142907022";
    private static final String ID_ROLE_ADMIN = "1477129745543204935";
    private static final String ID_CHANNEL_CHECKIN = "1493684466311364690";
    private static final String ID_CATEGORY_TEAMS = "1461604155687702743";

    private final JDA jda;

    public TeamService(JDA jda) {
        this.jda = jda;
    }

    public ConfirmTeamResponse confirmTeam(ConfirmTeamRequest request) {
        logger.info("--- NHẬN REQUEST: Team={}, Captain={}, Members={}",
                request.teamName(), request.captainDiscord(), request.membersDiscord());

        Guild guild = jda.getGuilds().isEmpty() ? null : jda.getGuilds().get(0);
        if (guild == null)
            return ConfirmTeamResponse.error("Bot chưa tham gia server!");

        // Validate Roles & Category
        Role roleTuyenThu = guild.getRoleById(ID_ROLE_TUYEN_THU);
        Role roleDoiTruong = guild.getRoleById(ID_ROLE_DOI_TRUONG);
        Role roleAdmin = guild.getRoleById(ID_ROLE_ADMIN);
        Category teamCategory = guild.getCategoryById(ID_CATEGORY_TEAMS);

        if (roleTuyenThu == null || roleDoiTruong == null || teamCategory == null) {
            return ConfirmTeamResponse.error("Lỗi cấu hình Server (Role/Category ID sai).");
        }

        // 1. Tìm Captain
        Member captainMember = findMemberByUsername(guild, request.captainDiscord());
        if (captainMember == null) {
            return ConfirmTeamResponse.error("Không tìm thấy Captain: " + request.captainDiscord());
        }

        // 2. Tìm Members (Dùng Set để tránh trùng lặp)
        Set<Member> finalMembers = new LinkedHashSet<>();
        finalMembers.add(captainMember);
        List<String> notFound = new ArrayList<>();

        for (String username : request.membersDiscord()) {
            if (username == null || username.isBlank())
                continue;
            String clean = username.trim();
            if (clean.equalsIgnoreCase(request.captainDiscord().trim()))
                continue;

            Member m = findMemberByUsername(guild, clean);
            if (m == null)
                notFound.add(clean);
            else
                finalMembers.add(m);
        }

        if (!notFound.isEmpty()) {
            return ConfirmTeamResponse.error("Không tìm thấy members: " + String.join(", ", notFound));
        }

        List<Member> memberList = new ArrayList<>(finalMembers);
        String textChannelName = "chat-" + request.shortName().toLowerCase().replace(" ", "-");
        String voiceChannelName = "voice-" + request.shortName().toLowerCase().replace(" ", "-");

        TextChannel existingText = findTextChannelByName(guild, textChannelName);

        if (existingText != null) {
            return handleUpdate(guild, existingText, request, memberList, captainMember,
                    roleTuyenThu, roleDoiTruong, roleAdmin, voiceChannelName);
        } else {
            return handleCreate(guild, teamCategory, request, memberList, captainMember,
                    roleTuyenThu, roleDoiTruong, roleAdmin, textChannelName, voiceChannelName);
        }
    }

    private ConfirmTeamResponse handleCreate(Guild guild, Category category, ConfirmTeamRequest request,
            List<Member> members, Member captain, Role roleTuyenThu,
            Role roleDoiTruong, Role roleAdmin, String textName, String voiceName) {

        assignRoles(guild, members, captain, roleTuyenThu, roleDoiTruong);

        try {
            // Tạo Text Channel
            TextChannel textChannel = category.createTextChannel(textName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(roleAdmin, EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .complete();

            // Set quyền cho từng member
            for (Member m : members) {
                textChannel.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
            }

            // Tạo Voice Channel
            VoiceChannel voiceChannel = category.createVoiceChannel(voiceName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(roleAdmin, EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .complete();

            for (Member m : members) {
                voiceChannel.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
            }

            sendWelcomeMessage(textChannel, request.teamName(), captain, members);
            return ConfirmTeamResponse.created("Đã tạo team " + request.teamName());
        } catch (Exception e) {
            return ConfirmTeamResponse.error("Lỗi tạo room: " + e.getMessage());
        }
    }

    private ConfirmTeamResponse handleUpdate(Guild guild, TextChannel textChannel, ConfirmTeamRequest request,
            List<Member> newMembers, Member captain, Role roleTuyenThu,
            Role roleDoiTruong, Role roleAdmin, String voiceName) {

        // Lấy danh sách ID cũ từ Permission Overrides (Né lỗi Cache)
        Set<String> oldMemberIds = textChannel.getPermissionOverrides().stream()
                .filter(ov -> ov.isMemberOverride())
                .map(ov -> ov.getId())
                .collect(Collectors.toSet());

        Set<String> newMemberIds = newMembers.stream().map(Member::getId).collect(Collectors.toSet());

        // Thành viên bị xóa
        Set<String> toRemoveIds = new HashSet<>(oldMemberIds);
        toRemoveIds.removeAll(newMemberIds);

        // Thành viên mới cần thêm
        List<Member> toAdd = newMembers.stream()
                .filter(m -> !oldMemberIds.contains(m.getId()))
                .collect(Collectors.toList());

        VoiceChannel voiceChannel = findVoiceChannelByName(guild, voiceName);

        // 1. Xóa thành viên cũ (Lột role + Cấm vào room)
        for (String id : toRemoveIds) {
            UserSnowflake user = UserSnowflake.fromId(id);
            guild.removeRoleFromMember(user, roleTuyenThu).queue();
            guild.removeRoleFromMember(user, roleDoiTruong).queue();
            textChannel.getManager().removePermissionOverride(Long.parseLong(id)).queue();
            if (voiceChannel != null)
                voiceChannel.getManager().removePermissionOverride(Long.parseLong(id)).queue();
        }

        // 2. Thêm thành viên mới (Set role + Cấp quyền)
        for (Member m : toAdd) {
            guild.addRoleToMember(m, roleTuyenThu).queue();
            textChannel.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
            if (voiceChannel != null)
                voiceChannel.upsertPermissionOverride(m).setAllowed(EnumSet.of(Permission.VIEW_CHANNEL)).queue();
        }

        // 3. Cập nhật Captain
        // Lột Đội Trưởng của tất cả ID cũ (trừ captain hiện tại)
        for (String oldId : oldMemberIds) {
            if (!oldId.equals(captain.getId())) {
                guild.removeRoleFromMember(UserSnowflake.fromId(oldId), roleDoiTruong).queue();
            }
        }
        guild.addRoleToMember(captain, roleDoiTruong).queue();
        guild.addRoleToMember(captain, roleTuyenThu).queue();

        // 4. Thông báo và Tag
        StringBuilder sb = new StringBuilder("🔄 **ĐỘI ĐÃ ĐƯỢC CẬP NHẬT**\n\n");
        if (!toAdd.isEmpty()) {
            sb.append("✅ **Thành viên mới:** ")
                    .append(toAdd.stream().map(Member::getAsMention).collect(Collectors.joining(" "))).append("\n");
        }
        if (!toRemoveIds.isEmpty()) {
            sb.append("❌ **Thành viên rời:** ")
                    .append(toRemoveIds.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" ")))
                    .append("\n");
        }
        sb.append("\n👑 **Đội trưởng:** ").append(captain.getAsMention());
        sb.append("\n👥 **Đội hình hiện tại:** ")
                .append(newMembers.stream().map(Member::getAsMention).collect(Collectors.joining(" ")));

        textChannel.sendMessage(sb.toString()).queue();
        return ConfirmTeamResponse.updated("Đã cập nhật team " + request.teamName());
    }

    private void assignRoles(Guild guild, List<Member> members, Member captain, Role tuyenThu, Role doiTruong) {
        for (Member m : members)
            guild.addRoleToMember(m, tuyenThu).queue();
        guild.addRoleToMember(captain, doiTruong).queue();
    }

    private void sendWelcomeMessage(TextChannel channel, String teamName, Member captain, List<Member> members) {
        String mentions = members.stream().map(Member::getAsMention).collect(Collectors.joining(" "));
        String msg = String.format(
                "🔥 **TEAM %s ĐÃ ĐƯỢC DUYỆT!** 🔥\n\nXin chào: %s\n\n" +
                        "Đội trưởng %s qua channel <#%s> check-in nhé!\n" +
                        "Chúc anh em thi đấu tốt! 🚀",
                teamName, mentions, captain.getAsMention(), ID_CHANNEL_CHECKIN);
        channel.sendMessage(msg).queue();
    }

    private Member findMemberByUsername(Guild guild, String username) {
        if (username == null || username.isBlank())
            return null;
        String clean = username.trim().toLowerCase().replace("@", "");

        // 1. Tìm trong cache
        List<Member> cached = guild.getMembersByName(clean, true);
        if (!cached.isEmpty())
            return cached.get(0);

        // 2. Tìm bằng API (né lỗi cache)
        try {
            List<Member> fetched = guild.retrieveMembersByPrefix(clean, 10).get();
            for (Member m : fetched) {
                if (m.getUser().getName().equalsIgnoreCase(clean))
                    return m;
            }
        } catch (Exception e) {
            logger.error("Lỗi fetch member {}: {}", clean, e.getMessage());
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