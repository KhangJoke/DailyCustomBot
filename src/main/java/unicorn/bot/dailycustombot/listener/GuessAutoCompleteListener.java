package unicorn.bot.dailycustombot.listener;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Xử lý gợi ý tự động cho lệnh Slash Command.
 */
public class GuessAutoCompleteListener extends ListenerAdapter {

    public static final List<String> VAL_RANKS = List.of(
            "val_iron", "val_bronze", "val_silver", "val_gold", "val_platinum", "val_diamond", "val_ascendant", "val_immortal", "val_radiant"
    );

    public static final List<String> LOL_RANKS = List.of(
            "lol_iron", "lol_bronze", "lol_silver", "lol_gold", "lol_platinum", "lol_emerald", "lol_diamond", "lol_master", "lol_grandmaster", "lol_challenger"
    );

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals("guess_post") && event.getFocusedOption().getName().equals("actual_rank")) {
            String game = event.getOption("game") != null ? event.getOption("game").getAsString() : "";

            List<String> ranks = switch (game.toUpperCase()) {
                case "VALORANT" -> VAL_RANKS;
                case "LOL" -> LOL_RANKS;
                default -> Stream.concat(VAL_RANKS.stream(), LOL_RANKS.stream()).toList();
            };

            String userInput = event.getFocusedOption().getValue().toLowerCase();
            List<Command.Choice> options = ranks.stream()
                    .filter(word -> word.toLowerCase().contains(userInput))
                    .limit(25)
                    .map(word -> new Command.Choice(word, word))
                    .collect(Collectors.toList());

            event.replyChoices(options).queue();
        }
    }
}
