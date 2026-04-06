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
        Integer winnerCount = event.getOption("winner_count", OptionMapping::getAsInt);

        if (messageId == null || winnerCount == null) {
            event.reply("❌ Thiếu Message ID hoặc số người trúng thưởng!").setEphemeral(true).queue();
            return;
        }

        if (winnerCount <= 0) {
            event.reply("❌ Số lượng người trúng giải phải lớn hơn 0!").setEphemeral(true).queue();
            return;
        }

        GuessGameManager gameManager = GuessGameManager.getInstance();
        String finalReward = gameManager.getReward(messageId).orElse("10k (1 giờ chơi)");

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

        // Lấy định dạng emoji chuẩn để in ra Embed
        var emotes = event.getGuild().getEmojisByName(actualRankEmoji, true);
        String formattedRank = emotes.isEmpty() ? "**" + actualRankEmoji + "**" : emotes.get(0).getAsMention();

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
                    "Đáp án đúng là " + formattedRank + " nhưng rất tiếc không có ai đoán đúng lần này!";
            event.reply(msg).queue();
            return;
        }

        // Chọn ngẫu nhiên N người
        java.util.Collections.shuffle(correctGuessers, random);
        int actualWinners = Math.min(winnerCount, correctGuessers.size());
        List<String> winners = correctGuessers.subList(0, actualWinners);

        StringBuilder winnersListText = new StringBuilder();
        for (String wId : winners) {
            winnersListText.append("<@").append(wId).append("> ");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 KẾT QUẢ MINI GAME ĐOÁN RANK 🎉")
                .setColor(Color.GREEN)
                .setDescription("Đáp án chính xác là: " + formattedRank + "\n\n" +
                        "Chúc mừng " + winnersListText.toString().trim() + " đã may mắn trúng thưởng!\n\n" +
                        "🎁 **Phần thưởng:** " + finalReward + "\n" +
                        "⏳ **Lưu ý:** Vui lòng tạo Ticket ở <#1490273404735983807> với cú pháp: Tên tài khoản Unicorn + Ngày nhận thưởng (ví dụ: hoangtan123 + 23/5) trong vòng 3 ngày (72h) kể từ kết quả được công bố để được nhận phần thưởng. Trường hợp tạo ticket quá hạn 3 ngày thì phần thưởng sẽ bị hủy.")
                .setFooter("Cảm ơn tất cả mọi người đã tham gia!");

        // Trả lời công khai
        event.replyEmbeds(embed.build()).queue();
    }
}
