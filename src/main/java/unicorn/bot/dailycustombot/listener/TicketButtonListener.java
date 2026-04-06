package unicorn.bot.dailycustombot.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.embed.TicketEmbedFactory;
import unicorn.bot.dailycustombot.model.TicketType;

import java.util.List;

/**
 * Listener xử lý tất cả button interactions liên quan đến hệ thống ticket.
 * - ticket_create: Hiện Select Menu chọn loại ticket
 * - ticket_close: Đóng ticket (xóa channel)
 * - ticket_claim: Staff claim ticket
 */
public class TicketButtonListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketButtonListener.class);

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        switch (buttonId) {
            case "ticket_create" -> handleCreateButton(event);
            case "ticket_close" -> handleCloseButton(event);
            case "ticket_claim" -> handleClaimButton(event);
            // Ignore buttons not related to ticket system
        }
    }

    /**
     * Xử lý nút "Tạo Ticket" — hiện Select Menu để chọn loại ticket.
     */
    private void handleCreateButton(ButtonInteractionEvent event) {
        List<TicketType> types = TicketConfigManager.getInstance().getTicketTypes();

        if (types.isEmpty()) {
            event.reply("❌ Chưa có loại ticket nào được cấu hình!").setEphemeral(true).queue();
            return;
        }

        // Build Select Menu với các loại ticket
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("ticket_type_select")
                .setPlaceholder("Chọn loại ticket...")
                .setMinValues(1)
                .setMaxValues(1);

        for (TicketType type : types) {
            menuBuilder.addOption(
                    type.label(),
                    type.id(),
                    type.description(),
                    net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode(type.emoji())
            );
        }

        event.reply("**📋 Vui lòng chọn loại ticket:**")
                .addActionRow(menuBuilder.build())
                .setEphemeral(true)
                .queue();

        logger.info("Ticket create menu shown to user: {} ({})", event.getUser().getName(), event.getUser().getId());
    }

    /**
     * Xử lý nút "Đóng Ticket" — xóa channel ticket.
     */
    private void handleCloseButton(ButtonInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        Guild guild = event.getGuild();
        User closedBy = event.getUser();

        if (guild == null) return;

        // Kiểm tra xem channel name có phải ticket channel không
        if (!channel.getName().startsWith("ticket-")) {
            event.reply("❌ Đây không phải kênh ticket!").setEphemeral(true).queue();
            return;
        }

        event.reply("🔒 Ticket sẽ được đóng trong 5 giây...").queue();

        // Lấy thông tin để log
        String channelName = channel.getName();
        // Cố gắng lấy topic để biết người tạo, topic format: "Ticket by <@userId>"
        String topic = channel.getTopic();
        String creatorMention = topic != null ? topic.replace("Ticket by ", "") : "Không rõ";

        // Gửi log
        String logChannelId = TicketConfigManager.getInstance().getLogChannelId();
        if (logChannelId != null && !logChannelId.equals("000000000000000000")) {
            TextChannel logChannel = guild.getTextChannelById(logChannelId);
            if (logChannel != null) {
                logChannel.sendMessageEmbeds(
                        TicketEmbedFactory.buildTicketCloseLogEmbed(closedBy, channelName, creatorMention)
                ).queue(
                        success -> logger.info("Ticket close logged: {}", channelName),
                        error -> logger.warn("Failed to log ticket close: {}", error.getMessage())
                );
            }
        }

        // Xóa channel sau 5 giây
        channel.delete().reason("Ticket closed by " + closedBy.getName())
                .queueAfter(5, java.util.concurrent.TimeUnit.SECONDS,
                        success -> logger.info("Ticket channel deleted: {} by {}", channelName, closedBy.getName()),
                        error -> logger.error("Failed to delete ticket channel: {}", error.getMessage())
                );
    }

    /**
     * Xử lý nút "Claim Ticket" — staff nhận xử lý ticket.
     */
    private void handleClaimButton(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) return;

        // Kiểm tra staff role
        String staffRoleId = TicketConfigManager.getInstance().getStaffRoleId();
        Role staffRole = guild.getRoleById(staffRoleId);

        boolean isStaff = member.hasPermission(Permission.ADMINISTRATOR)
                || (staffRole != null && member.getRoles().contains(staffRole));

        if (!isStaff) {
            event.reply("❌ Bạn không có quyền claim ticket! Chỉ staff mới có thể thực hiện.").setEphemeral(true).queue();
            return;
        }

        // Gửi embed thông báo claim
        event.replyEmbeds(TicketEmbedFactory.buildTicketClaimEmbed(event.getUser())).queue();

        logger.info("Ticket claimed by staff: {} ({}) in channel: {}",
                event.getUser().getName(), event.getUser().getId(), event.getChannel().getName());
    }
}
