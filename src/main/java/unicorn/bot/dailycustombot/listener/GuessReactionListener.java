package unicorn.bot.dailycustombot.listener;

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

        // Kiểm tra xem user có lựa chọn cũ nào trong DB không
        String prevGuess = gameManager.getPreviousGuess(messageId, user.getId());

        // Ghi đè vào Database (Lưu ngay lập tức để chặn spam)
        gameManager.upsertGuess(messageId, user.getId(), emojiDb);

        // Phát hiện đổi đáp án: Nếu có lựa chọn cũ khác với lựa chọn mới, gỡ UI của lựa chọn cũ
        if (prevGuess != null && !prevGuess.equals(emojiDb)) {
            event.retrieveMessage().queue(msg -> {
                msg.getReactions().stream()
                        .filter(r -> r.getEmoji().getName().equals(prevGuess))
                        .findFirst()
                        .ifPresent(oldReaction -> oldReaction.removeReaction(user).queue());
            });
        }
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
