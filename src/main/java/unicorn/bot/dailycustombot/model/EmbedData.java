package unicorn.bot.dailycustombot.model;

/**
 * Record chứa toàn bộ dữ liệu để render Discord Embed cho một tựa game.
 */
public record EmbedData(
        String championPrize,
        String killPrize,
        String formatDescription,
        String gun,
        String map,
        String agent,
        String registerDeadline,
        String matchTime,
        String rankLimit,
        String ageLimit,
        String registerLink,
        String supportChannelId,
        String thumbnailUrl,
        String footerIconUrl
) {
}
