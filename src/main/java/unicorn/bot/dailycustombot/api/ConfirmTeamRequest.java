package unicorn.bot.dailycustombot.api;

import java.util.List;

/**
 * DTO nhận request từ Google Apps Script khi admin tick checkbox trên Sheet.
 *
 * @param teamName        Tên đầy đủ của đội (cột C)
 * @param shortName       Tên viết tắt dùng cho tên kênh (cột D)
 * @param captainDiscord  Discord username của đội trưởng (cột I)
 * @param membersDiscord  Danh sách Discord usernames thành viên 2-6
 * @param rowNumber       Số dòng trên sheet để track trạng thái
 */
public record ConfirmTeamRequest(
        String teamName,
        String shortName,
        String captainDiscord,
        List<String> membersDiscord,
        int rowNumber
) {
}
