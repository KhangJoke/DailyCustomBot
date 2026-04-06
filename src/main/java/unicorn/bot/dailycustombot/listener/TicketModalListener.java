package unicorn.bot.dailycustombot.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.embed.TicketEmbedFactory;
import unicorn.bot.dailycustombot.model.TicketType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Listener xử lý Modal submit cho ticket system.
 * Nhận lý do từ user → tạo channel ticket → gửi embed thông tin.
 */
public class TicketModalListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketModalListener.class);

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        // Chỉ xử lý modal của ticket system: "ticket_modal:<typeId>"
        if (!modalId.startsWith("ticket_modal:")) {
            return;
        }

        String typeId = modalId.substring("ticket_modal:".length());
        String reason = event.getValue("ticket_reason").getAsString();
        User user = event.getUser();
        Guild guild = event.getGuild();

        if (guild == null) {
            event.reply("❌ Lệnh này chỉ hoạt động trong server!").setEphemeral(true).queue();
            return;
        }

        TicketConfigManager configManager = TicketConfigManager.getInstance();

        // Tìm loại ticket
        Optional<TicketType> ticketTypeOpt = configManager.getTicketType(typeId);
        if (ticketTypeOpt.isEmpty()) {
            event.reply("❌ Loại ticket không hợp lệ!").setEphemeral(true).queue();
            return;
        }

        TicketType ticketType = ticketTypeOpt.get();

        // Lấy category
        String categoryId = configManager.getTicketCategoryId();
        Category category = guild.getCategoryById(categoryId);

        if (category == null) {
            event.reply("❌ Category ticket chưa được cấu hình đúng! Liên hệ admin.").setEphemeral(true).queue();
            logger.error("Ticket category not found: {}", categoryId);
            return;
        }

        // Lấy ticket number
        int ticketNumber = configManager.getNextTicketNumber();
        String channelName = String.format("ticket-%04d-%s", ticketNumber, sanitizeUsername(user.getName()));

        // Defer reply vì tạo channel mất thời gian
        event.deferReply(true).queue();

        // Lấy staff role
        String staffRoleId = configManager.getStaffRoleId();
        Role staffRole = guild.getRoleById(staffRoleId);

        // Tạo channel ticket trong category
        guild.createTextChannel(channelName, category)
                .setTopic("Ticket by " + user.getAsMention())
                .addPermissionOverride(guild.getPublicRole(),
                        Collections.emptyList(),
                        EnumSet.of(Permission.VIEW_CHANNEL)) // Ẩn với @everyone
                .addMemberPermissionOverride(user.getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES),
                        Collections.emptyList()) // User tạo ticket được xem
                .addPermissionOverride(guild.getSelfMember(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL),
                        Collections.emptyList()) // Bot cần quyền
                .queue(ticketChannel -> {
                    // Thêm permission cho staff role nếu có
                    if (staffRole != null) {
                        ticketChannel.upsertPermissionOverride(staffRole)
                                .setAllowed(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES)
                                .queue();
                    }

                    // Gửi embed vào channel ticket
                    ticketChannel.sendMessage(user.getAsMention())
                            .setEmbeds(TicketEmbedFactory.buildTicketOpenEmbed(user, ticketType, reason, ticketNumber))
                            .setActionRow(
                                    Button.danger("ticket_close", "🔒 Đóng Ticket"),
                                    Button.primary("ticket_claim", "✋ Nhận xử lý")
                            )
                            .queue();

                    // Reply cho user biết
                    event.getHook().editOriginal(
                            String.format("✅ Ticket đã được tạo! Vui lòng qua %s để trao đổi.", ticketChannel.getAsMention())
                    ).queue();

                    // Log
                    String logChannelId = configManager.getLogChannelId();
                    if (logChannelId != null && !logChannelId.equals("000000000000000000")) {
                        TextChannel logChannel = guild.getTextChannelById(logChannelId);
                        if (logChannel != null) {
                            logChannel.sendMessageEmbeds(
                                    TicketEmbedFactory.buildTicketOpenLogEmbed(user, ticketType, ticketNumber, ticketChannel.getAsMention())
                            ).queue();
                        }
                    }

                    logger.info("Ticket #{} created by {} ({}) - Type: {} - Channel: {}",
                            ticketNumber, user.getName(), user.getId(), ticketType.label(), channelName);

                }, error -> {
                    event.getHook().editOriginal("❌ Không thể tạo ticket! Lỗi: " + error.getMessage()).queue();
                    logger.error("Failed to create ticket channel: {}", error.getMessage(), error);
                });
    }

    /**
     * Sanitize username để dùng làm tên channel Discord.
     * Chỉ cho phép chữ thường, số, dấu gạch ngang.
     */
    private String sanitizeUsername(String username) {
        return username.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
