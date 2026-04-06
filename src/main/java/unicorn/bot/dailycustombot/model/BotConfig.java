package unicorn.bot.dailycustombot.model;

import java.util.List;

/**
 * Record wrapper chứa danh sách cấu hình tất cả các game.
 * Map trực tiếp với dữ liệu trong PostgreSQL.
 */
public record BotConfig(
        List<GameConfig> games
) {
}
