package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.model.TicketType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler cho lệnh /ticket_remove_type.
 * Xóa loại ticket khỏi hệ thống.
 * Yêu cầu quyền Administrator.
 */
public class TicketRemoveTypeCommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketRemoveTypeCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        // Kiểm tra quyền admin
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn cần quyền **Administrator** để sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String id = event.getOption("id", OptionMapping::getAsString);

        if (id == null) {
            event.reply("❌ Vui lòng cung cấp ID loại ticket cần xóa!").setEphemeral(true).queue();
            return;
        }

        boolean removed = TicketConfigManager.getInstance().removeTicketType(id);

        if (!removed) {
            // Hiện danh sách ID hiện có để user biết
            List<TicketType> currentTypes = TicketConfigManager.getInstance().getTicketTypes();
            String available = currentTypes.stream()
                    .map(t -> "`" + t.id() + "` (" + t.emoji() + " " + t.label() + ")")
                    .collect(Collectors.joining(", "));

            event.reply("❌ Không tìm thấy loại ticket với ID `" + id + "`!\n\n**Các loại hiện có:** " + available)
                    .setEphemeral(true).queue();
            return;
        }

        logger.info("Ticket type removed by {}: {}", event.getUser().getName(), id);

        event.reply(String.format("""
                ✅ **Đã xóa loại ticket:** `%s`
                
                > ⚠️ Nếu bạn đã gửi panel ticket trước đó, hãy gửi lại bằng `/ticket_setup` để cập nhật danh sách.
                """, id)).setEphemeral(true).queue();
    }
}
