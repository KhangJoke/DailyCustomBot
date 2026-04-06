package unicorn.bot.dailycustombot.model;

import java.util.List;

/**
 * Record chứa toàn bộ cấu hình hệ thống ticket.
 * Map trực tiếp với cấu trúc file ticket-config.json.
 *
 * @param ticketCategoryId ID Category trên Discord để tạo channel ticket
 * @param staffRoleId      ID Role staff có quyền xem/xử lý tất cả ticket
 * @param logChannelId     ID Channel để ghi log sự kiện ticket (mở/đóng)
 * @param ticketCounter    Bộ đếm tự tăng cho mã ticket
 * @param ticketTypes      Danh sách các loại ticket
 */
public record TicketConfig(
        String ticketCategoryId,
        String staffRoleId,
        String logChannelId,
        int ticketCounter,
        List<TicketType> ticketTypes
) {
}
