package unicorn.bot.dailycustombot.listener;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.command.DailyAddCommand;
import unicorn.bot.dailycustombot.command.DailyAutoCommand;
import unicorn.bot.dailycustombot.command.DailyPostNowCommand;
import unicorn.bot.dailycustombot.command.DailyRemoveCommand;
import unicorn.bot.dailycustombot.command.DailyUpdateCommand;
import unicorn.bot.dailycustombot.command.DailyViewCommand;
import unicorn.bot.dailycustombot.command.TicketSetupCommand;
import unicorn.bot.dailycustombot.command.TicketAddTypeCommand;
import unicorn.bot.dailycustombot.command.TicketRemoveTypeCommand;
import unicorn.bot.dailycustombot.command.GuessPostCommand;
import unicorn.bot.dailycustombot.command.GuessResultCommand;

/**
 * Listener nhận mọi Slash Command interaction và route đến handler tương ứng.
 */
public class SlashCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SlashCommandListener.class);

    private final DailyUpdateCommand dailyUpdateCommand = new DailyUpdateCommand();
    private final DailyAutoCommand dailyAutoCommand = new DailyAutoCommand();
    private final DailyPostNowCommand dailyPostNowCommand = new DailyPostNowCommand();
    private final DailyViewCommand dailyViewCommand = new DailyViewCommand();
    private final DailyAddCommand dailyAddCommand = new DailyAddCommand();
    private final DailyRemoveCommand dailyRemoveCommand = new DailyRemoveCommand();
    private final TicketSetupCommand ticketSetupCommand = new TicketSetupCommand();
    private final TicketAddTypeCommand ticketAddTypeCommand = new TicketAddTypeCommand();
    private final TicketRemoveTypeCommand ticketRemoveTypeCommand = new TicketRemoveTypeCommand();
    private final GuessPostCommand guessPostCommand = new GuessPostCommand();
    private final GuessResultCommand guessResultCommand = new GuessResultCommand();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        logger.info("Received command /{} from user {} ({})",
                commandName,
                event.getUser().getName(),
                event.getUser().getId());

        switch (commandName) {
            case "daily_update" -> dailyUpdateCommand.handle(event);
            case "daily_auto" -> dailyAutoCommand.handle(event);
            case "daily_post_now" -> dailyPostNowCommand.handle(event);
            case "daily_view" -> dailyViewCommand.handle(event);
            case "daily_add" -> dailyAddCommand.handle(event);
            case "daily_remove" -> dailyRemoveCommand.handle(event);
            case "ticket_setup" -> ticketSetupCommand.handle(event);
            case "ticket_add_type" -> ticketAddTypeCommand.handle(event);
            case "ticket_remove_type" -> ticketRemoveTypeCommand.handle(event);
            case "guess_post" -> guessPostCommand.handle(event);
            case "guess_result" -> guessResultCommand.handle(event);
            default -> {
                logger.warn("Unknown command: /{}", commandName);
                event.reply("Unknown command!").setEphemeral(true).queue();
            }
        }
    }
}
