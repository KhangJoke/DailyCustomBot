package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import unicorn.bot.dailycustombot.config.PermissionManager;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PermViewCommand {

    public void handle(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn cần quyền **Administrator** để sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Map<String, Set<String>> permissions = PermissionManager.getInstance().getGuildPermissions(guildId);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🛡️ Bảng phân quyền Role hiện tại");
        eb.setColor(Color.decode("#5865F2")); // Discord blurple
        eb.setDescription("Danh sách các role được phép sử dụng từng nhóm lệnh. Admin (" + Permission.ADMINISTRATOR.getName() + " permission) luôn có sẵn quyền cho tất cả!");

        boolean hasAnyPerms = false;

        for (String group : PermissionManager.VALID_GROUPS) {
            Set<String> roleIds = permissions.get(group);
            if (roleIds != null && !roleIds.isEmpty()) {
                hasAnyPerms = true;
                String rolesStr = roleIds.stream()
                        .map(id -> "<@&" + id + ">")
                        .collect(Collectors.joining(", "));
                eb.addField("Nhóm lệnh: `" + group + "`", rolesStr, false);
            } else {
                eb.addField("Nhóm lệnh: `" + group + "`", "*Chưa có role nào được cấp*", false);
            }
        }

        if (!hasAnyPerms) {
            eb.setFooter("Bảng phân quyền trống. Mọi người (trừ Admin) sẽ bị chặn sử dụng các lệnh này.");
        } else {
            eb.setFooter("Dùng /perm_add hoặc /perm_remove để thay đổi.");
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
}
