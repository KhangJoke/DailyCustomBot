package unicorn.bot.dailycustombot.model;

/**
 * Record chứa toàn bộ dữ liệu để render Discord Embed cho một tựa game.
 * Sử dụng tên field generic để hỗ trợ nhiều game khác nhau:
 * - detail1: Valorant → Súng, LoL → Chế độ, Generic → Chi tiết 1
 * - map: Map chung cho mọi game
 * - detail2: Valorant → Agent, LoL → Tướng, Generic → Chi tiết 2
 */
public record EmbedData(
        String championPrize,
        String killPrize,
        String formatDescription,
        String detail1,
        String map,
        String detail2,
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
