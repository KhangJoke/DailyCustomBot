package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.PermissionManager;

public class PermAddCommand {
    private static final Logger logger = LoggerFactory.getLogger(PermAddCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn cần quyền **Administrator** để sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        Role role = event.getOption("role", OptionMapping::getAsRole);
        String group = event.getOption("group", OptionMapping::getAsString);

        if (role == null || group == null) {
            event.reply("❌ Thiếu tham số bắt buộc!").setEphemeral(true).queue();
            return;
        }

        if (!PermissionManager.VALID_GROUPS.contains(group)) {
             event.reply("❌ Nhóm lệnh không hợp lệ! Vui lòng chọn: " + String.join(", ", PermissionManager.VALID_GROUPS))
                     .setEphemeral(true).queue();
             return;
        }

        if (PermissionManager.PERMISSION_GROUP.equals(group)) {
             event.reply("❌ Không thể gán quyền cho nhóm `" + PermissionManager.PERMISSION_GROUP + "`. Nhóm này chỉ dành cho Administrator.")
                     .setEphemeral(true).queue();
             return;
        }

        String guildId = event.getGuild().getId();
        boolean success = PermissionManager.getInstance().addRolePermission(guildId, role.getId(), group);

        if (success) {
            event.reply("✅ Đã cấp quyền sử dụng nhóm lệnh `" + group + "` cho role " + role.getAsMention() + "!")
                    .setEphemeral(true).queue();
        } else {
            event.reply("⚠️ Role " + role.getAsMention() + " đã có quyền trong nhóm `" + group + "` từ trước!")
                    .setEphemeral(true).queue();
        }
    }
}
