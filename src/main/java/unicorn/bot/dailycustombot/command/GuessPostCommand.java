package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import unicorn.bot.dailycustombot.config.GuessGameManager;

import java.awt.Color;

/**
 * Lệnh /guess_post cho Admin đăng mini game.
 */
public class GuessPostCommand {

    public static final String[] RANK_EMOJIS = {
            "1\uFE0F\u20E3", "2\uFE0F\u20E3", "3\uFE0F\u20E3", "4\uFE0F\u20E3", "5\uFE0F\u20E3",
            "6\uFE0F\u20E3", "7\uFE0F\u20E3", "8\uFE0F\u20E3", "9\uFE0F\u20E3", "\uD83D\uDD1F"
    };

    public void handle(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn không có quyền sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String videoUrl = event.getOption("video_url", OptionMapping::getAsString);
        String actualRank = event.getOption("actual_rank", OptionMapping::getAsString);

        if (videoUrl == null || actualRank == null) {
            event.reply("❌ Thiếu tham số!").setEphemeral(true).queue();
            return;
        }

        // Defer reply để bot có thời gian xử lý post
        event.deferReply(true).queue();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎮 MINI GAME: ĐOÁN RANK")
                .setColor(Color.ORANGE)
                .setDescription("Xem clip bên trên và đoán xem người chơi đang ở mức rank nào nhé!\n" +
                        "Phần thưởng: **10k (1 giờ chơi) cho 1 bạn may mắn đoán trúng!**\n\n" +
                        "*Lưu ý: Bạn bè chỉ được chọn 1 biểu tượng duy nhất.*")
                .addField("⭐ Danh sách Chọn",
                        """
                        1️⃣ Sắt (Iron)
                        2️⃣ Đồng (Bronze)
                        3️⃣ Bạc (Silver)
                        4️⃣ Vàng (Gold)
                        5️⃣ Bạch kim (Platinum)
                        6️⃣ Kim cương (Diamond)
                        7️⃣ Lục bảo / Đăng cấp (Emrd/Ascd)
                        8️⃣ Cao thủ / Bất tử (Master/Immo)
                        9️⃣ Đại Cao thủ / Thách đấu Val
                        🔟 Thách đấu LOL (Challenger)
                        """, false)
                .setFooter("Admin sẽ chốt kết quả vào ngày mai!");

        // Gửi Message vào channel hiện tại (Nội dung text là link để Discord tự bung video player)
        event.getChannel().sendMessage(videoUrl)
                .addEmbeds(embed.build())
                .queue(message -> {
                    // Lưu vào DB
                    GuessGameManager.getInstance().createSession(message.getId(), videoUrl, actualRank);

                    // Thả reaction
                    for (String emojiUnicode : RANK_EMOJIS) {
                        message.addReaction(Emoji.fromUnicode(emojiUnicode)).queue();
                    }

                    // Trả lời admin
                    event.getHook().sendMessage("✅ Đã tạo Mini Game thành công! Hãy kiểm tra kênh.")
                            .queue();
                });
    }
}
