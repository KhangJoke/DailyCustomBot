package unicorn.bot.dailycustombot.api;

/**
 * DTO trả response về cho Google Apps Script.
 *
 * @param success true nếu xử lý thành công
 * @param message Thông báo chi tiết
 * @param action  Hành động đã thực hiện: "CREATED", "UPDATED", "CANCELLED",
 *                "NOTIFIED", hoặc "ERROR"
 */
public record ConfirmTeamResponse(
        boolean success,
        String message,
        String action) {
    public static ConfirmTeamResponse created(String message) {
        return new ConfirmTeamResponse(true, message, "CREATED");
    }

    public static ConfirmTeamResponse updated(String message) {
        return new ConfirmTeamResponse(true, message, "UPDATED");
    }

    public static ConfirmTeamResponse cancelled(String message) {
        return new ConfirmTeamResponse(true, message, "CANCELLED");
    }

    public static ConfirmTeamResponse notified(String message) {
        return new ConfirmTeamResponse(true, message, "NOTIFIED");
    }

    public static ConfirmTeamResponse error(String message) {
        return new ConfirmTeamResponse(false, message, "ERROR");
    }
}

