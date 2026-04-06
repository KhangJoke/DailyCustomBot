package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.GuessGameManager;
import unicorn.bot.dailycustombot.listener.GuessAutoCompleteListener;

import java.awt.Color;
import java.util.List;

/**
 * Lệnh /guess_post cho Admin đăng mini game.
 */
public class GuessPostCommand {

    private static final Logger logger = LoggerFactory.getLogger(GuessPostCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Bạn không có quyền sử dụng lệnh này!").setEphemeral(true).queue();
            return;
        }

        String game = event.getOption("game", OptionMapping::getAsString);
        String videoUrl = event.getOption("video_url", OptionMapping::getAsString);
        String actualRank = event.getOption("actual_rank", OptionMapping::getAsString);
        String reward = event.getOption("reward", OptionMapping::getAsString);

        if (game == null || videoUrl == null || actualRank == null) {
            event.reply("❌ Thiếu tham số!").setEphemeral(true).queue();
            return;
        }

        String finalReward = reward != null && !reward.trim().isEmpty() ? reward : "10k (1 giờ chơi) cho 1 bạn may mắn đoán trúng!";

        // Defer reply để bot có thời gian đọc và tải emoji
        event.deferReply(true).queue();

        boolean isValorant = "VALORANT".equalsIgnoreCase(game);
        List<String> ranksList = isValorant ? GuessAutoCompleteListener.VAL_RANKS : GuessAutoCompleteListener.LOL_RANKS;
        String gameText = isValorant ? "VALORANT" : "LIÊN MINH HUYỀN THOẠI";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎮 MINI GAME: ĐOÁN RANK " + gameText)
                .setColor(isValorant ? Color.RED : Color.CYAN)
                .setDescription("Xem clip bên trên và đoán xem người chơi đang ở mức rank nào nhé!\n" +
                        "Phần thưởng: **" + finalReward + "**\n\n" +
                        "*Lưu ý: Hệ thống chỉ lưu một lựa chọn cuối cùng của bạn.*")
                .setFooter("Admin sẽ chốt kết quả vào ngày mai!");

        // Gửi Video Clip riêng ra một tin nhắn trước để Discord tự render giao diện
        // khung Player
        event.getChannel().sendMessage(videoUrl).queue();

        // Gửi Embed thông báo Game và dán Reaction xuống dưới
        event.getChannel().sendMessageEmbeds(embed.build())
                .queue(message -> {
                    // Lưu vào DB
                    GuessGameManager.getInstance().createSession(message.getId(), game.toUpperCase(), videoUrl,
                            actualRank);

                    // Thả reaction
                    for (String rankName : ranksList) {
                        var emotes = message.getGuild().getEmojisByName(rankName, true);
                        if (!emotes.isEmpty()) {
                            message.addReaction(emotes.get(0)).queue();
                        } else {
                            logger.warn("Custom emoji not found on server: {}", rankName);
                        }
                    }

                    // Trả lời admin
                    event.getHook().sendMessage("✅ Đã tạo Mini Game thành công! Hãy kiểm tra bài post.")
                            .queue();
                });
    }
}
