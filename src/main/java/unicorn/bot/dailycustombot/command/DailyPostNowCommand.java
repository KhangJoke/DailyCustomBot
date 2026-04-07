package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.config.PermissionManager;
import unicorn.bot.dailycustombot.embed.EmbedFactory;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.util.Optional;

/**
 * Handler cho lệnh /daily_post_now.
 * Ép bot đăng bài ngay lập tức cho tựa game được chọn.
 */
public class DailyPostNowCommand {

    private static final Logger logger = LoggerFactory.getLogger(DailyPostNowCommand.class);

    public void handle(SlashCommandInteractionEvent event) {
        if (!PermissionManager.getInstance().hasAccess(event.getMember(), "daily")) {
            event.reply("❌ Bạn không có quyền sử dụng lệnh này (Yêu cầu quyền hạn quản lý giải đấu)!")
                    .setEphemeral(true).queue();
            return;
        }

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

        // Tìm channel để gửi
        TextChannel channel = event.getJDA().getTextChannelById(game.channelId());
        if (channel == null) {
            event.reply("❌ Cannot find channel `%s`! Check channelId in config."
                            .formatted(game.channelId()))
                    .setEphemeral(true).queue();
            return;
        }

        // Gửi Embed kèm ping role
        String pingContent = "||<@&%s>||".formatted(game.roleId());

        channel.sendMessage(pingContent)
                .setEmbeds(EmbedFactory.buildEmbed(game))
                .queue(
                        success -> {
                            logger.info("Posted Daily Custom for {} to #{}", gameName, channel.getName());
                            event.reply("✅ Posted **%s** to <#%s>!".formatted(gameName, game.channelId()))
                                    .setEphemeral(true).queue();
                        },
                        error -> {
                            logger.error("Failed to send message for {}: {}", gameName, error.getMessage());
                            event.reply("❌ Failed to send message: %s".formatted(error.getMessage()))
                                    .setEphemeral(true).queue();
                        }
                );
    }
}
