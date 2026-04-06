package unicorn.bot.dailycustombot.model;

/**
 * Record đại diện cấu hình cho một tựa game trong hệ thống Daily Custom.
 * Bao gồm thông tin hệ thống (channel, role, auto-post) và dữ liệu embed.
 */
public record GameConfig(
        String gameName,
        String channelId,
        String roleId,
        boolean autoPost,
        String postTime,
        EmbedData embedData
) {
    /**
     * Tạo bản sao với embedData mới (immutable update pattern cho Record).
     */
    public GameConfig withEmbedData(EmbedData newEmbedData) {
        return new GameConfig(gameName, channelId, roleId, autoPost, postTime, newEmbedData);
    }

    /**
     * Tạo bản sao với autoPost và postTime mới.
     */
    public GameConfig withAutoPost(boolean newAutoPost, String newPostTime) {
        return new GameConfig(gameName, channelId, roleId, newAutoPost, newPostTime, embedData);
    }
}
