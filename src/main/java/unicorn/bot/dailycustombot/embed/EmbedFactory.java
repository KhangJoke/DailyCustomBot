package unicorn.bot.dailycustombot.embed;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import unicorn.bot.dailycustombot.model.EmbedData;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Factory tạo Discord MessageEmbed từ GameConfig.
 * Hỗ trợ render khác nhau theo từng tựa game:
 * - Valorant: Súng / Map / Agent
 * - LoL: Chế độ / Map / Tướng
 * - Generic: Chi tiết 1 / Map / Chi tiết 2
 */
public class EmbedFactory {

        private static final Color EMBED_COLOR = new Color(0xE56A1E);
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

        private EmbedFactory() {
                // Utility class
        }

        /**
         * Build MessageEmbed hoàn chỉnh từ config của một game.
         * Tự động chọn layout phù hợp theo gameName.
         */
        public static MessageEmbed buildEmbed(GameConfig gameConfig) {
                String normalized = gameConfig.gameName().toLowerCase().trim();
                return switch (normalized) {
                        case "valorant", "val" -> buildValorantEmbed(gameConfig);
                        case "lol", "league of legends", "lmht", "liên minh" -> buildLoLEmbed(gameConfig);
                        default -> buildGenericEmbed(gameConfig);
                };
        }

        // ===== VALORANT =====
        private static MessageEmbed buildValorantEmbed(GameConfig gameConfig) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);

                String title = "▬▬▬ DAILY DEATHMATCH CUSTOM (%s) ▬▬▬".formatted(todayDate);

                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                String formatContent = """
                                __**CUSTOM DEATHMATCH**__
                                ```text
                                %s
                                ```
                                • **Súng:** %s
                                • **Map:** %s
                                • **Agent:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                data.formatDescription(),
                                data.detail1(),
                                data.map(),
                                data.detail2(),
                                data.registerDeadline(),
                                data.matchTime(),
                                data.rankLimit(),
                                data.ageLimit());

                String registerContent = "• **Link đăng ký:** [Đăng ký Daily Custom](%s)"
                                .formatted(data.registerLink());

                String noteContent = """
                                • Mọi vấn đề xin vui lòng liên hệ mod tại <#%s>.
                                • Vui lòng đăng ký sớm để giữ slot, số lượng có hạn!"""
                                .formatted(data.supportChannelId());

                return new EmbedBuilder()
                                .setTitle(title)
                                .setColor(EMBED_COLOR)
                                .addField("🟠 GIẢI THƯỞNG", prizeContent, false)
                                .addField("🟠 THỂ THỨC", formatContent, false)
                                .addField("🟠 MỞ ĐĂNG KÝ", registerContent, false)
                                .addField("🟠 LƯU Ý", noteContent, false)
                                .setThumbnail(data.thumbnailUrl())
                                .setFooter("THE DEATHMATCH KING", data.footerIconUrl())
                                .build();
        }

        // ===== LEAGUE OF LEGENDS =====
        private static MessageEmbed buildLoLEmbed(GameConfig gameConfig) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);

                String title = "▬▬▬ DAILY CUSTOM LEAGUE OF LEGENDS (%s) ▬▬▬".formatted(todayDate);

                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                String formatContent = """
                                __**CUSTOM MATCH**__
                                ```text
                                %s
                                ```
                                • **Chế độ:** %s
                                • **Map:** %s
                                • **Tướng:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                data.formatDescription(),
                                data.detail1(),
                                data.map(),
                                data.detail2(),
                                data.registerDeadline(),
                                data.matchTime(),
                                data.rankLimit(),
                                data.ageLimit());

                String registerContent = "• **Link đăng ký:** [Đăng ký Daily Custom](%s)"
                                .formatted(data.registerLink());

                String noteContent = """
                                • Mọi vấn đề xin vui lòng liên hệ mod tại <#%s>.
                                • Vui lòng đăng ký sớm để giữ slot, số lượng có hạn!"""
                                .formatted(data.supportChannelId());

                return new EmbedBuilder()
                                .setTitle(title)
                                .setColor(new Color(0x0BC6E3)) // Màu xanh LoL
                                .addField("🔵 GIẢI THƯỞNG", prizeContent, false)
                                .addField("🔵 THỂ THỨC", formatContent, false)
                                .addField("🔵 MỞ ĐĂNG KÝ", registerContent, false)
                                .addField("🔵 LƯU Ý", noteContent, false)
                                .setThumbnail(data.thumbnailUrl())
                                .setFooter("THE RIFT KING", data.footerIconUrl())
                                .build();
        }

        // ===== GENERIC (các game khác) =====
        private static MessageEmbed buildGenericEmbed(GameConfig gameConfig) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);
                String gameName = gameConfig.gameName().toUpperCase();

                String title = "▬▬▬ DAILY CUSTOM %s (%s) ▬▬▬".formatted(gameName, todayDate);

                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                String formatContent = """
                                __**CUSTOM MATCH**__
                                ```text
                                %s
                                ```
                                • **Chi tiết 1:** %s
                                • **Map:** %s
                                • **Chi tiết 2:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                data.formatDescription(),
                                data.detail1(),
                                data.map(),
                                data.detail2(),
                                data.registerDeadline(),
                                data.matchTime(),
                                data.rankLimit(),
                                data.ageLimit());

                String registerContent = "• **Link đăng ký:** [Đăng ký Daily Custom](%s)"
                                .formatted(data.registerLink());

                String noteContent = """
                                • Mọi vấn đề xin vui lòng liên hệ mod tại <#%s>.
                                • Vui lòng đăng ký sớm để giữ slot, số lượng có hạn!"""
                                .formatted(data.supportChannelId());

                return new EmbedBuilder()
                                .setTitle(title)
                                .setColor(EMBED_COLOR)
                                .addField("🟠 GIẢI THƯỞNG", prizeContent, false)
                                .addField("🟠 THỂ THỨC", formatContent, false)
                                .addField("🟠 MỞ ĐĂNG KÝ", registerContent, false)
                                .addField("🟠 LƯU Ý", noteContent, false)
                                .setThumbnail(data.thumbnailUrl())
                                .setFooter("DAILY CUSTOM", data.footerIconUrl())
                                .build();
        }
}
