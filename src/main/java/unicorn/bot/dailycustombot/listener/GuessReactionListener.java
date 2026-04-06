package unicorn.bot.dailycustombot.listener;

import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import unicorn.bot.dailycustombot.config.GuessGameManager;

import java.util.Optional;

/**
 * Lắng nghe lượt thả cảm xúc (Reaction) của người chơi.
 */
public class GuessReactionListener extends ListenerAdapter {

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        User user = event.getUser();
        if (user == null || user.isBot()) return;

        GuessGameManager gameManager = GuessGameManager.getInstance();
        String messageId = event.getMessageId();

        Optional<String> status = gameManager.getSessionStatus(messageId);
        if (status.isEmpty() || !"OPEN".equals(status.get())) {
            return;
        }

        String emojiDb = event.getEmoji().getName();

        // Đảm bảo chỉ có 1 reaction trên UI Discord bằng cách duyệt và gỡ các reaction cũ
        event.retrieveMessage().queue(msg -> {
            for (MessageReaction reaction : msg.getReactions()) {
                if (!reaction.getEmoji().getName().equals(emojiDb)) {
                    // Nếu user có thả ở cái khác, bắt bot gỡ nó đi
                    reaction.retrieveUsers().queue(users -> {
                        if (users.contains(user)) {
                            reaction.removeReaction(user).queue();
                        }
                    });
                }
            }
        });

        // Ghi/Đè vào Database (UPSERT)
        gameManager.upsertGuess(messageId, user.getId(), emojiDb);
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        User user = event.getUser();
        if (user == null || user.isBot()) return;

        GuessGameManager gameManager = GuessGameManager.getInstance();
        String messageId = event.getMessageId();

        Optional<String> status = gameManager.getSessionStatus(messageId);
        if (status.isEmpty() || !"OPEN".equals(status.get())) {
            return;
        }

        String emojiDb = event.getEmoji().getName();

        // Chỉ xóa trong DB nếu emoji vừa bị gỡ chính là emoji đang lưu trong DB.
        // Điều kiện này chống lỗi Race Condition khi bot tự động gỡ reaction cũ của user.
        gameManager.removeGuessExact(messageId, user.getId(), emojiDb);
    }
}
