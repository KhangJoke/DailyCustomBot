package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.model.TicketType;

/**
 * Handler cho lệnh /ticket_add_type.
 * Thêm loại ticket mới vào hệ thống.
 * Yêu cầu quyền Administrator.
 */
public class TicketAddTypeCommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketAddTypeCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        // Kiểm tra quyền admin
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn cần quyền **Administrator** để sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String id = event.getOption("id", OptionMapping::getAsString);
        String emoji = event.getOption("emoji", OptionMapping::getAsString);
        String label = event.getOption("label", OptionMapping::getAsString);
        String description = event.getOption("description", OptionMapping::getAsString);

        if (id == null || emoji == null || label == null || description == null) {
            event.reply("❌ Vui lòng cung cấp đầy đủ: `id`, `emoji`, `label`, `description`!").setEphemeral(true).queue();
            return;
        }

        // Validate id format (only lowercase letters, numbers, underscore)
        if (!id.matches("^[a-z0-9_]+$")) {
            event.reply("❌ ID chỉ được chứa chữ thường, số và dấu `_` (VD: `bug_report`)").setEphemeral(true).queue();
            return;
        }

        TicketType newType = new TicketType(id, emoji, label, description);
        boolean added = TicketConfigManager.getInstance().addTicketType(newType);

        if (!added) {
            event.reply("❌ Loại ticket với ID `" + id + "` đã tồn tại!").setEphemeral(true).queue();
            return;
        }

        logger.info("New ticket type added by {}: {} ({} {})",
                event.getUser().getName(), id, emoji, label);

        event.reply(String.format("""
                ✅ **Đã thêm loại ticket mới:**
                
                • **ID:** `%s`
                • **Emoji:** %s
                • **Tên:** %s
                • **Mô tả:** %s
                
                > ⚠️ Nếu bạn đã gửi panel ticket trước đó, hãy gửi lại bằng `/ticket_setup` để cập nhật danh sách.
                """, id, emoji, label, description)).setEphemeral(true).queue();
    }
}
