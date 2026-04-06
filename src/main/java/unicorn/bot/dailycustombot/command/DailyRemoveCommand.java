package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.DailyCustomBotApplication;
import unicorn.bot.dailycustombot.config.ConfigManager;

/**
 * Handler cho lệnh /daily_remove.
 * Xóa một tựa game ra khỏi hệ thống config.
 */
public class DailyRemoveCommand {

    private static final Logger logger = LoggerFactory.getLogger(DailyRemoveCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        String gameName = event.getOption("game", OptionMapping::getAsString);

        if (gameName == null) {
            event.reply("❌ Please select a game to remove!").setEphemeral(true).queue();
            return;
        }

        ConfigManager configManager = ConfigManager.getInstance();

        boolean removed = configManager.removeGameConfig(gameName);

        if (!removed) {
            event.reply("❌ Cannot find game **" + gameName + "** in the system!").setEphemeral(true).queue();
            return;
        }

        // Re-register slash commands with Discord để OptionChoices mất đi game này
        DailyCustomBotApplication.registerSlashCommands(event.getJDA());

        logger.info("Removed game config for: {}", gameName);

        event.reply("🗑️ Successfully removed game **" + gameName + "** from the system!").setEphemeral(true).queue();
    }
}
