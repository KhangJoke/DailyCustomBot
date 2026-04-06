package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.embed.TicketEmbedFactory;

/**
 * Handler cho lệnh /ticket_setup.
 * Gửi embed panel ticket vào channel chỉ định và cập nhật config.
 * Yêu cầu quyền Administrator.
 */
public class TicketSetupCommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketSetupCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        // Kiểm tra quyền admin
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn cần quyền **Administrator** để sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String categoryId = event.getOption("category_id", OptionMapping::getAsString);
        String staffRoleId = event.getOption("staff_role_id", OptionMapping::getAsString);
        String logChannelId = event.getOption("log_channel_id", "", OptionMapping::getAsString);

        if (categoryId == null || staffRoleId == null) {
            event.reply("❌ Vui lòng cung cấp đầy đủ: `category_id` và `staff_role_id`!").setEphemeral(true).queue();
            return;
        }

        // Validate category exists
        if (event.getGuild().getCategoryById(categoryId) == null) {
            event.reply("❌ Category ID `" + categoryId + "` không tồn tại! Hãy kiểm tra lại.").setEphemeral(true).queue();
            return;
        }

        // Validate staff role exists
        if (event.getGuild().getRoleById(staffRoleId) == null) {
            event.reply("❌ Staff Role ID `" + staffRoleId + "` không tồn tại! Hãy kiểm tra lại.").setEphemeral(true).queue();
            return;
        }

        // Nếu log_channel_id trống, đặt giá trị placeholder
        final String finalLogChannelId = logChannelId.isBlank() ? "000000000000000000" : logChannelId;

        // Cập nhật config
        TicketConfigManager.getInstance().updateConfig(categoryId, staffRoleId, finalLogChannelId);

        // Xác định channel để gửi panel
        TextChannel targetChannel = event.getOption("channel", () -> event.getChannel().asTextChannel(),
                opt -> {
                    TextChannel ch = opt.getAsChannel().asTextChannel();
                    return ch != null ? ch : event.getChannel().asTextChannel();
                });

        // Gửi embed panel + nút tạo ticket
        targetChannel.sendMessageEmbeds(TicketEmbedFactory.buildTicketPanelEmbed())
                .setActionRow(
                        Button.success("ticket_create", "🎫 Tạo Ticket")
                )
                .queue(
                        success -> {
                            event.reply(String.format("""
                                    ✅ **Ticket System đã được thiết lập!**
                                    
                                    • 📌 Panel ticket: %s
                                    • 📂 Category: `%s`
                                    • 👥 Staff role: <@&%s>
                                    • 📋 Log channel: %s
                                    
                                    Thành viên có thể bấm nút **Tạo Ticket** để tạo ticket mới.
                                    """,
                                    targetChannel.getAsMention(),
                                    categoryId,
                                    staffRoleId,
                                    finalLogChannelId.equals("000000000000000000") ? "Chưa cấu hình" : "<#" + finalLogChannelId + ">"
                            )).setEphemeral(true).queue();

                            logger.info("Ticket system setup by {} in channel {} (category: {}, staff: {})",
                                    event.getUser().getName(), targetChannel.getName(), categoryId, staffRoleId);
                        },
                        error -> {
                            event.reply("❌ Không thể gửi panel ticket! Lỗi: " + error.getMessage()).setEphemeral(true).queue();
                            logger.error("Failed to send ticket panel: {}", error.getMessage());
                        }
                );
    }
}
