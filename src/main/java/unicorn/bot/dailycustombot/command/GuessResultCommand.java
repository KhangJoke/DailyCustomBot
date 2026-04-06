package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import unicorn.bot.dailycustombot.config.GuessGameManager;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Lệnh /guess_result cho Admin quay kết quả.
 */
public class GuessResultCommand {

    private final Random random = new Random();

    public void handle(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn không có quyền sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String messageId = event.getOption("message_id", OptionMapping::getAsString);
        if (messageId == null) {
            event.reply("❌ Thiếu Message ID!").setEphemeral(true).queue();
            return;
        }

        GuessGameManager gameManager = GuessGameManager.getInstance();

        Optional<String> optStatus = gameManager.getSessionStatus(messageId);
        if (optStatus.isEmpty()) {
            event.reply("❌ Không tìm thấy Session Mini Game với Message ID này!").setEphemeral(true).queue();
            return;
        }

        if ("CLOSED".equalsIgnoreCase(optStatus.get())) {
            event.reply("❌ Mini game này đã được công bố kết quả rồi!").setEphemeral(true).queue();
            return;
        }

        Optional<String> optRank = gameManager.getActualRank(messageId);
        if (optRank.isEmpty()) {
            event.reply("❌ Lỗi dữ liệu: Không tìm thấy đáp án!").setEphemeral(true).queue();
            return;
        }

        String actualRankEmoji = optRank.get();
        List<String> correctGuessers = gameManager.getCorrectGuessers(messageId, actualRankEmoji);

        // Đóng game
        gameManager.updateStatus(messageId, "CLOSED");

        event.getChannel().retrieveMessageById(messageId).queue(msg -> {
            // Xóa toàn bộ reaction để không ai bấm thêm được
            msg.clearReactions().queue();
        }, failure -> {
            // Không làm gì nếu message gốc bị xóa
        });

        if (correctGuessers.isEmpty()) {
            String msg = "😔 **MINI GAME KẾT THÚC!**\n" +
                    "Đáp án đúng là " + actualRankEmoji + " nhưng rất tiếc không có ai đoán đúng lần này!";
            event.reply(msg).queue();
            return;
        }

        // Chọn ngẫu nhiên 1 người
        String winnerId = correctGuessers.get(random.nextInt(correctGuessers.size()));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 KẾT QUẢ MINI GAME ĐOÁN RANK 🎉")
                .setColor(Color.GREEN)
                .setDescription("Đáp án chính xác là: **" + actualRankEmoji + "**\n\n" +
                        "Chúc mừng <@" + winnerId + "> đã may mắn trúng thưởng!\n\n" +
                        "🎁 **Phần thưởng:** 10k (1 giờ chơi)\n" +
                        "⏳ **Lưu ý:** Vui lòng tạo Ticket hoặc nhắn tin cho Admin báo Tên Tài Khoản trong vòng **3 ngày** để được nạp, nếu không phần thưởng sẽ bị hủy.")
                .setFooter("Cảm ơn tất cả mọi người đã tham gia!");

        // Trả lời công khai
        event.replyEmbeds(embed.build()).queue();
    }
}
