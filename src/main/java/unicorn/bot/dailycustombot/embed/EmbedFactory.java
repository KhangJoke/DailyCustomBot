package unicorn.bot.dailycustombot.embed;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import unicorn.bot.dailycustombot.model.EmbedData;
import unicorn.bot.dailycustombot.model.GameConfig;
import unicorn.bot.dailycustombot.model.GameType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Factory tạo Discord MessageEmbed từ GameConfig.
 * Hỗ trợ render khác nhau theo từng tựa game dựa trên {@link GameType}.
 */
public class EmbedFactory {

        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

        private EmbedFactory() {
                // Utility class
        }

        /**
         * Build MessageEmbed hoàn chỉnh từ config của một game.
         * Tự động chọn layout phù hợp theo gameName thông qua GameType.
         */
        public static MessageEmbed buildEmbed(GameConfig gameConfig) {
                GameType type = GameType.fromName(gameConfig.gameName());
                return switch (type) {
                        case VALORANT -> buildValorantEmbed(gameConfig, type);
                        case LOL -> buildLoLEmbed(gameConfig, type);
                        case GENERIC -> buildGenericEmbed(gameConfig, type);
                };
        }

        // ===== VALORANT =====
        private static MessageEmbed buildValorantEmbed(GameConfig gameConfig, GameType type) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);

                String title = "▬▬▬ DAILY DEATHMATCH CUSTOM (%s) ▬▬▬".formatted(todayDate);

                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                String formatContent = """
                                __**%s**__
                                ```text
                                %s
                                ```
                                • **%s:** %s
                                • **Map:** %s
                                • **%s:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                type.subHeader(),
                                data.formatDescription(),
                                type.detail1Label(), data.detail1(),
                                data.map(),
                                type.detail2Label(), data.detail2(),
                                data.registerDeadline(),
                                data.matchTime(),
                                data.rankLimit(),
                                data.ageLimit());

                String registerContent = "• **Link đăng ký:** [Đăng ký Daily Custom](%s)"
                                .formatted(data.registerLink());

                String noteContent = """
                                • Mọi vấn đề xin vui lòng liên hệ mod tại <#1483141799961694259> hoặc tạo ticket tại <#1490273404735983807>.
                                • Vui lòng đăng ký sớm để giữ slot, số lượng có hạn!""";

                String emoji = type.fieldEmoji();
                return new EmbedBuilder()
                                .setTitle(title)
                                .setColor(type.embedColor())
                                .addField(emoji + " GIẢI THƯỞNG", prizeContent, false)
                                .addField(emoji + " THỂ THỨC", formatContent, false)
                                .addField(emoji + " MỞ ĐĂNG KÝ", registerContent, false)
                                .addField(emoji + " LƯU Ý", noteContent, false)
                                .setThumbnail(data.thumbnailUrl())
                                .setFooter(type.footerText(), data.footerIconUrl())
                                .build();
        }

        // ===== LEAGUE OF LEGENDS =====
        private static MessageEmbed buildLoLEmbed(GameConfig gameConfig, GameType type) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);

                String title = "▬▬DAILY CUSTOM LEAGUE OF LEGENDS (%s)▬▬".formatted(todayDate);

                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                String formatContent = """
                                __**%s**__
                                ```text
                                %s
                                ```
                                • **%s:** %s
                                • **Map:** %s
                                • **%s:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                type.subHeader(),
                                data.formatDescription(),
                                type.detail1Label(), data.detail1(),
                                data.map(),
                                type.detail2Label(), data.detail2(),
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

                String emoji = type.fieldEmoji();
                return new EmbedBuilder()
                                .setTitle(title)
                                .setColor(type.embedColor())
                                .addField(emoji + " GIẢI THƯỞNG", prizeContent, false)
                                .addField(emoji + " THỂ THỨC", formatContent, false)
                                .addField(emoji + " MỞ ĐĂNG KÝ", registerContent, false)
                                .addField(emoji + " LƯU Ý", noteContent, false)
                                .setThumbnail(data.thumbnailUrl())
                                .setFooter(type.footerText(), data.footerIconUrl())
                                .build();
        }

        // ===== GENERIC (các game khác) =====
        private static MessageEmbed buildGenericEmbed(GameConfig gameConfig, GameType type) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);
                String gameName = gameConfig.gameName().toUpperCase();

                String title = "▬▬▬ DAILY CUSTOM %s (%s) ▬▬▬".formatted(gameName, todayDate);

                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                String formatContent = """
                                __**%s**__
                                ```text
                                %s
                                ```
                                • **%s:** %s
                                • **Map:** %s
                                • **%s:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                type.subHeader(),
                                data.formatDescription(),
                                type.detail1Label(), data.detail1(),
                                data.map(),
                                type.detail2Label(), data.detail2(),
                                data.registerDeadline(),
                                data.matchTime(),
                                data.rankLimit(),
                                data.ageLimit());

                String registerContent = "• **Link đăng ký:** [Đăng ký Daily Custom](%s)"
                                .formatted(data.registerLink());

                String noteContent = """
                                • Mọi vấn đề xin vui lòng liên hệ mod tại <#1483141799961694259> hoặc tạo ticket tại <#1490273404735983807>.
                                • Vui lòng đăng ký sớm để giữ slot, số lượng có hạn!""";

                String emoji = type.fieldEmoji();
                return new EmbedBuilder()
                                .setTitle(title)
                                .setColor(type.embedColor())
                                .addField(emoji + " GIẢI THƯỞNG", prizeContent, false)
                                .addField(emoji + " THỂ THỨC", formatContent, false)
                                .addField(emoji + " MỞ ĐĂNG KÝ", registerContent, false)
                                .addField(emoji + " LƯU Ý", noteContent, false)
                                .setThumbnail(data.thumbnailUrl())
                                .setFooter(type.footerText(), data.footerIconUrl())
                                .build();
        }
}
