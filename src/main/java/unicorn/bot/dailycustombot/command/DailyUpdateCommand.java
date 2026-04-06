package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.model.EmbedData;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.util.Optional;

/**
 * Handler cho lệnh /daily_update.
 * Cập nhật các trường embed trong config.json mà không đăng bài.
 */
public class DailyUpdateCommand {

    private static final Logger logger = LoggerFactory.getLogger(DailyUpdateCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        String gameName = event.getOption("game", OptionMapping::getAsString);
        if (gameName == null) {
            event.reply("❌ Please select a game!").setEphemeral(true).queue();
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
        EmbedData oldData = game.embedData();

        // Lấy giá trị mới từ options, fallback về giá trị cũ nếu không truyền
        EmbedData newData = new EmbedData(
                getOptionOrDefault(event, "champion_prize", oldData.championPrize()),
                getOptionOrDefault(event, "kill_prize", oldData.killPrize()),
                getOptionOrDefault(event, "format_description", oldData.formatDescription()),
                getOptionOrDefault(event, "gun", oldData.gun()),
                getOptionOrDefault(event, "map", oldData.map()),
                getOptionOrDefault(event, "agent", oldData.agent()),
                getOptionOrDefault(event, "register_deadline", oldData.registerDeadline()),
                getOptionOrDefault(event, "match_time", oldData.matchTime()),
                getOptionOrDefault(event, "rank_limit", oldData.rankLimit()),
                getOptionOrDefault(event, "age_limit", oldData.ageLimit()),
                getOptionOrDefault(event, "register_link", oldData.registerLink()),
                oldData.supportChannelId(),
                oldData.thumbnailUrl(),
                oldData.footerIconUrl()
        );

        GameConfig updatedGame = game.withEmbedData(newData);
        configManager.updateGameConfig(updatedGame);

        logger.info("Updated config for game: {}", gameName);

        String reply = """
                ✅ Successfully updated config for **%s**!
                
                • **Map:** %s
                • **Gun:** %s
                • **Agent:** %s
                • **Register:** %s
                • **Match:** %s
                
                Use `/daily_post_now %s` to post immediately.""".formatted(
                gameName,
                newData.map(),
                newData.gun(),
                newData.agent(),
                newData.registerDeadline(),
                newData.matchTime(),
                gameName
        );

        event.reply(reply).setEphemeral(true).queue();
    }

    private String getOptionOrDefault(SlashCommandInteractionEvent event, String optionName, String defaultValue) {
        OptionMapping option = event.getOption(optionName);
        return option != null ? option.getAsString() : defaultValue;
    }
}
