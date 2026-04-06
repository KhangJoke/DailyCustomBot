package unicorn.bot.dailycustombot.model;

/**
 * Record đại diện cho một loại ticket.
 * Mỗi loại ticket có id duy nhất, emoji, label hiển thị, và mô tả.
 */
public record TicketType(
        String id,
        String emoji,
        String label,
        String description
) {
}
