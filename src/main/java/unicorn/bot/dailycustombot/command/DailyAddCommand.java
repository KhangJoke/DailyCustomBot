package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.DailyCustomBotApplication;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.model.GameConfig;

/**
 * Handler cho lệnh /daily_add.
 * Thêm một tựa game mới vào hệ thống với template mặc định từ ConfigManager.
 */
public class DailyAddCommand {

    private static final Logger logger = LoggerFactory.getLogger(DailyAddCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        String gameName = event.getOption("game", OptionMapping::getAsString);
        String channelId = event.getOption("channel_id", OptionMapping::getAsString);
        String roleId = event.getOption("role_id", OptionMapping::getAsString);

        if (gameName == null || channelId == null || roleId == null) {
            event.reply("❌ Please provide required info: game, channel_id, role_id!").setEphemeral(true).queue();
            return;
        }

        ConfigManager configManager = ConfigManager.getInstance();

        // Kiểm tra xem đã có chưa
        if (configManager.getGameConfig(gameName).isPresent()) {
            event.reply("❌ Game **" + gameName + "** already exists! Use `/daily_update` to modify.").setEphemeral(true).queue();
            return;
        }

        // Tạo mới GameConfig sử dụng tham số mặc định
        GameConfig newGame = new GameConfig(
                gameName,
                channelId,
                roleId,
                false, // Auto-post off by default
                "", // No default time
                configManager.getDefaultEmbedTemplate()
        );

        // Lưu vào config
        configManager.addGameConfig(newGame);

        // Re-register slash commands with Discord để OptionChoices xổ ra có game này
        DailyCustomBotApplication.registerSlashCommands(event.getJDA());

        logger.info("Added new game config for: {}", gameName);

        String reply = """
                ✅ Successfully added new game: **%s**
                • Notification channel: <#%s>
                • Ping role: <@&%s>
                
                All tournament format and prize information has been set to defaults.
                Use the command `/daily_update game:%s` to customize it!
                """;
        event.reply(String.format(reply, gameName, channelId, roleId, gameName)).setEphemeral(true).queue();
    }
}
