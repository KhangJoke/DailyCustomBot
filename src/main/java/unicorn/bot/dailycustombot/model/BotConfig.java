package unicorn.bot.dailycustombot.model;

import java.util.List;

/**
 * Record wrapper chứa danh sách cấu hình tất cả các game.
 * Map trực tiếp với cấu trúc gốc của file config.json.
 */
public record BotConfig(
        List<GameConfig> games
) {
}
