package unicorn.bot.dailycustombot.embed;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.model.TicketType;

import java.awt.*;
import java.time.Instant;
import java.util.List;

/**
 * Factory tạo tất cả Discord MessageEmbed cho hệ thống ticket.
 */
public class TicketEmbedFactory {

    private static final Color PANEL_COLOR = new Color(0x5865F2);   // Discord blurple
    private static final Color TICKET_COLOR = new Color(0x2ECC71);  // Green
    private static final Color CLOSE_COLOR = new Color(0xE74C3C);   // Red
    private static final Color CLAIM_COLOR = new Color(0xF39C12);   // Orange/Yellow

    private TicketEmbedFactory() {
        // Utility class
    }

    /**
     * Build embed panel chính hiển thị trên channel setup.
     * Liệt kê tất cả loại ticket có sẵn.
     */
    public static MessageEmbed buildTicketPanelEmbed() {
        List<TicketType> types = TicketConfigManager.getInstance().getTicketTypes();

        StringBuilder description = new StringBuilder();
        description.append("Chào mừng bạn đến kênh hỗ trợ! 🎫\n\n");
        description.append("Bấm nút **Tạo Ticket** bên dưới để được hỗ trợ.\n");
        description.append("Hệ thống sẽ tạo một kênh riêng cho bạn.\n\n");
        description.append("**📋 Các loại ticket hiện có:**\n");

        for (TicketType type : types) {
            description.append(String.format("%s **%s** — %s\n", type.emoji(), type.label(), type.description()));
        }

        description.append("\n> ⏳ Staff sẽ phản hồi trong thời gian sớm nhất!");

        return new EmbedBuilder()
                .setTitle("🎫 HỆ THỐNG HỖ TRỢ — TICKET")
                .setDescription(description.toString())
                .setColor(PANEL_COLOR)
                .setFooter("Bấm nút bên dưới để tạo ticket")
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Build embed gửi vào channel ticket khi vừa được tạo.
     */
    public static MessageEmbed buildTicketOpenEmbed(User user, TicketType ticketType, String reason, int ticketNumber) {
        String ticketId = String.format("#%04d", ticketNumber);

        return new EmbedBuilder()
                .setTitle(String.format("🎫 Ticket %s — %s %s", ticketId, ticketType.emoji(), ticketType.label()))
                .setColor(TICKET_COLOR)
                .addField("👤 Người tạo", user.getAsMention(), true)
                .addField("📂 Loại ticket", ticketType.emoji() + " " + ticketType.label(), true)
                .addField("🔢 Mã ticket", ticketId, true)
                .addField("📝 Lý do / Mô tả", reason, false)
                .setFooter("Ticket được tạo bởi " + user.getName())
                .setThumbnail(user.getEffectiveAvatarUrl())
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Build embed log khi ticket được mở.
     */
    public static MessageEmbed buildTicketOpenLogEmbed(User user, TicketType ticketType, int ticketNumber, String channelMention) {
        String ticketId = String.format("#%04d", ticketNumber);

        return new EmbedBuilder()
                .setTitle("📬 Ticket Mới Được Tạo")
                .setColor(TICKET_COLOR)
                .addField("🔢 Mã ticket", ticketId, true)
                .addField("📂 Loại", ticketType.emoji() + " " + ticketType.label(), true)
                .addField("👤 Người tạo", user.getAsMention(), true)
                .addField("📌 Kênh", channelMention, true)
                .setFooter("Ticket System Log")
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Build embed log khi ticket được đóng.
     */
    public static MessageEmbed buildTicketCloseLogEmbed(User closedBy, String ticketChannelName, String creatorMention) {
        return new EmbedBuilder()
                .setTitle("📪 Ticket Đã Đóng")
                .setColor(CLOSE_COLOR)
                .addField("🎫 Ticket", ticketChannelName, true)
                .addField("👤 Người tạo", creatorMention, true)
                .addField("🔒 Đóng bởi", closedBy.getAsMention(), true)
                .setFooter("Ticket System Log")
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Build embed thông báo claim ticket.
     */
    public static MessageEmbed buildTicketClaimEmbed(User staff) {
        return new EmbedBuilder()
                .setDescription(String.format("✅ %s đã nhận xử lý ticket này.", staff.getAsMention()))
                .setColor(CLAIM_COLOR)
                .setTimestamp(Instant.now())
                .build();
    }
}
