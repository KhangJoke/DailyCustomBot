package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.config.PermissionManager;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * Handler cho lệnh /daily_view.
 * Xem cấu hình hiện tại của một tựa game, hoặc liệt kê tất cả game.
 */
public class DailyViewCommand {

    private static final Logger logger = LoggerFactory.getLogger(DailyViewCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        if (!PermissionManager.getInstance().hasAccess(event.getMember(), "daily")) {
            event.reply("❌ Bạn không có quyền sử dụng lệnh này (Yêu cầu quyền hạn quản lý giải đấu)!")
                    .setEphemeral(true).queue();
            return;
        }

        String gameName = event.getOption("game", OptionMapping::getAsString);
        ConfigManager configManager = ConfigManager.getInstance();

        if (gameName == null) {
            // View tất cả game
            List<String> games = configManager.getGameNames();
            if (games.isEmpty()) {
                event.reply("❌ No games found in the system.").setEphemeral(true).queue();
                return;
            }
            String list = String.join("\n• ", games);
            event.reply("📋 **List of available games:**\n• " + list).setEphemeral(true).queue();
            return;
        }

        // View 1 game cụ thể
        Optional<GameConfig> optGame = configManager.getGameConfig(gameName);
        if (optGame.isEmpty()) {
            event.reply("❌ Cannot find game **" + gameName + "** in config!").setEphemeral(true).queue();
            return;
        }

        GameConfig game = optGame.get();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("⚙️ Config: " + game.gameName());
        eb.setColor(Color.decode("#E56A1E"));

        eb.addField("Channel ID", "`" + game.channelId() + "`", true);
        eb.addField("Role ID to ping", "`" + game.roleId() + "`", true);
        eb.addBlankField(true);
        eb.addField("Auto Post", game.autoPost() ? "🟢 ON" : "🔴 OFF", true);
        eb.addField("Post Time", game.postTime().isEmpty() ? "Not set" : "`" + game.postTime() + "`", true);
        eb.addBlankField(true);

        StringBuilder embedDetails = new StringBuilder();
        embedDetails.append("**Map:** ").append(game.embedData().map()).append("\n");
        embedDetails.append("**Gun:** ").append(game.embedData().gun()).append("\n");
        embedDetails.append("**Agent:** ").append(game.embedData().agent()).append("\n");
        embedDetails.append("**Rank/Age:** ").append(game.embedData().rankLimit()).append(" / ").append(game.embedData().ageLimit()).append("\n");
        embedDetails.append("**Register:** ").append(game.embedData().registerDeadline()).append("\n");
        embedDetails.append("**Match:** ").append(game.embedData().matchTime()).append("\n");
        eb.addField("Tournament Details", embedDetails.toString(), false);

        logger.info("Viewed config for game: {}", gameName);
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
}
