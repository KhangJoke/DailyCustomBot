package unicorn.bot.dailycustombot.api;

import java.util.List;

/**
 * DTO nhận request từ Google Apps Script khi admin thao tác trên Sheet.
 *
 * @param teamName        Tên đầy đủ của đội (cột C)
 * @param shortName       Tên viết tắt dùng cho tên kênh (cột D)
 * @param captainDiscord  Discord username của đội trưởng (cột I)
 * @param membersDiscord  Danh sách Discord usernames thành viên 2-6
 * @param rowNumber       Số dòng trên sheet để track trạng thái
 * @param action          Hành động: "CONFIRM" (tạo/cập nhật) hoặc "CANCEL" (hủy đội)
 */
public record ConfirmTeamRequest(
        String teamName,
        String shortName,
        String captainDiscord,
        List<String> membersDiscord,
        int rowNumber,
        String action
) {
}
