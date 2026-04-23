package unicorn.bot.dailycustombot.api;

import java.util.List;

/**
 * DTO nhận request từ Google Apps Script khi admin thao tác trên Sheet.
 *
 * @param teamName       Tên đầy đủ của đội (cột C)
 * @param shortName      Tên viết tắt dùng cho tên kênh (cột D) — có thể null khi action=NEW
 * @param captainDiscord Discord username của đội trưởng (cột I)
 * @param membersDiscord Danh sách Discord usernames thành viên 2-6
 * @param rowNumber      Số dòng trên sheet để track trạng thái
 * @param action         Hành động: "NEW", "CONFIRM", hoặc "CANCEL"
 * @param sheetUrl       URL Google Sheet (dùng cho action=NEW để gửi link cho admin)
 * @param sheetName      Tên sheet tab (dùng cho action=NEW)
 */
public record ConfirmTeamRequest(
        String teamName,
        String shortName,
        String captainDiscord,
        List<String> membersDiscord,
        int rowNumber,
        String action,
        String sheetUrl,
        String sheetName) {
}
