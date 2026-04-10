package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.config.PermissionManager;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.util.Optional;

/**
 * Handler cho lệnh /daily_auto.
 * Bật/tắt chế độ tự động đăng bài và set giờ cho tựa game.
 */
public class DailyAutoCommand {

    private static final Logger logger = LoggerFactory.getLogger(DailyAutoCommand.class);
    private static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    public void handle(SlashCommandInteractionEvent event) {
        if (!PermissionManager.getInstance().hasAccess(event.getMember(), "daily")) {
            event.reply("❌ Bạn không có quyền sử dụng lệnh này (Yêu cầu quyền hạn quản lý giải đấu)!")
                    .setEphemeral(true).queue();
            return;
        }

        String gameName = event.getOption("game", OptionMapping::getAsString);
        String toggle = event.getOption("toggle", OptionMapping::getAsString);

        if (gameName == null || toggle == null) {
            event.reply("❌ Please provide both game and ON/OFF status!")
                    .setEphemeral(true).queue();
            return;
        }

        ConfigManager configManager = ConfigManager.getInstance();
        Optional<GameConfig> optGame = configManager.getGameConfig(gameName);

        if (optGame.isEmpty()) {
            event.reply("❌ Cannot find game **%s** in config!".formatted(gameName))
                    .setEphemeral(true).queue();
            return;
        }

        GameConfig game = optGame.get();
        boolean autoPost = toggle.equalsIgnoreCase("ON");

        // Lấy time nếu bật auto, nếu không giữ nguyên
        String postTime = game.postTime();
        OptionMapping timeOption = event.getOption("time");
        if (timeOption != null) {
            postTime = timeOption.getAsString();
            if (!postTime.matches(TIME_PATTERN)) {
                event.reply("""
                        ❌ Sai định dạng giờ! Yêu cầu đúng format `HH:mm` (24 giờ, bắt buộc 2 chữ số).
                        ✅ Ví dụ đúng: `08:30`, `18:00`, `21:45`
                        ❌ Ví dụ sai: `8:30`, `6pm`, `18h00`""")
                        .setEphemeral(true).queue();
                return;
            }
        } else if (autoPost && game.postTime().isEmpty()) {
            event.reply("""
                    ❌ Bạn phải chỉ định giờ khi bật auto-post!
                    📌 Format: `HH:mm` (24 giờ, bắt buộc 2 chữ số)
                    ✅ Ví dụ: `/daily_auto game:%s toggle:ON time:18:00`""".formatted(gameName))
                    .setEphemeral(true).queue();
            return;
        }

        GameConfig updatedGame = game.withAutoPost(autoPost, postTime);
        configManager.updateGameConfig(updatedGame);

        logger.info("Auto-post for {}: {} at {}", gameName, autoPost ? "ON" : "OFF", postTime);

        String statusEmoji = autoPost ? "🟢" : "🔴";
        String statusText = autoPost ? "ON" : "OFF";
        String reply = """
                %s Auto-post for **%s** is now **%s**!
                ⏰ Post time: `%s`""".formatted(statusEmoji, gameName, statusText, postTime);

        event.reply(reply).setEphemeral(true).queue();
    }
}
