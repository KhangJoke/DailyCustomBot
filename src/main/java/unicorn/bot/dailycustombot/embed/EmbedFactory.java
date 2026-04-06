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
 * Bố cục chính xác theo spec: Title, 4 Fields, màu cam, Thumbnail, Footer.
 */
public class EmbedFactory {

        private static final Color EMBED_COLOR = new Color(0xE56A1E);
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

        private EmbedFactory() {
                // Utility class
        }

        /**
         * Build MessageEmbed hoàn chỉnh từ config của một game.
         */
        public static MessageEmbed buildEmbed(GameConfig gameConfig) {
                EmbedData data = gameConfig.embedData();
                String todayDate = LocalDate.now().format(DATE_FMT);

                // ===== TITLE =====
                String title = "▬▬▬ DAILY DEATHMATCH CUSTOM (%s) ▬▬▬".formatted(todayDate);

                // ===== FIELD 1: GIẢI THƯỞNG =====
                String prizeContent = """
                                • %s
                                • %s""".formatted(data.championPrize(), data.killPrize());

                // ===== FIELD 2: THỂ THỨC =====
                String formatContent = """
                                __**CUSTOM DEATHMATCH**__
                                ```text
                                %s
                                ```
                                • **Gun:** %s
                                • **Map:** %s
                                • **Agent:** %s
                                • **Đăng ký:** %s — Thi đấu: %s
                                • **Rank:** %s
                                • **Tuổi:** %s""".formatted(
                                data.formatDescription(),
                                data.gun(),
                                data.map(),
                                data.agent(),
                                data.registerDeadline(),
                                data.matchTime(),
                                data.rankLimit(),
                                data.ageLimit());

                // ===== FIELD 3: MỞ ĐĂNG KÝ =====
                String registerContent = "• **Link đăng ký:** [Đăng ký Daily Custom](%s)"
                                .formatted(data.registerLink());

                // ===== FIELD 4: LƯU Ý =====
                String noteContent = """
                                • Mọi vấn đề xin vui lòng liên hệ mod tại <#%s>.
                                • Vui lòng đăng ký sớm để giữ slot, số lượng có hạn!"""
                                .formatted(data.supportChannelId());

                // ===== BUILD EMBED =====
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
}
