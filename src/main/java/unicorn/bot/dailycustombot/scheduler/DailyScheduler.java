package unicorn.bot.dailycustombot.scheduler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.embed.EmbedFactory;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler chạy ngầm mỗi phút, kiểm tra config và tự động đăng bài
 * khi autoPost == true và giờ hiện tại khớp postTime.
 */
public class DailyScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyScheduler.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final JDA jda;
    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;

    /**
     * Set lưu các game đã post trong phút hiện tại để tránh gửi trùng lặp.
     * Format: "gameName-HH:mm"
     */
    private final Set<String> postedThisMinute = new HashSet<>();
    private String lastMinute = "";

    public DailyScheduler(JDA jda) {
        this.jda = jda;
        this.configManager = ConfigManager.getInstance();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DailyScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Khởi động scheduler, kiểm tra mỗi 30 giây.
     */
    public void start() {
        logger.info("DailyScheduler started. Checking every 30 seconds.");
        scheduler.scheduleAtFixedRate(this::tick, 0, 30, TimeUnit.SECONDS);
    }

    /**
     * Dừng scheduler.
     */
    public void shutdown() {
        logger.info("DailyScheduler shutting down...");
        scheduler.shutdown();
    }

    /**
     * Logic chạy mỗi tick: reload config, kiểm tra giờ, gửi embed nếu khớp.
     */
    private void tick() {
        try {
            String currentMinute = LocalTime.now().format(TIME_FMT);

            // Reset danh sách đã post khi sang phút mới
            if (!currentMinute.equals(lastMinute)) {
                postedThisMinute.clear();
                lastMinute = currentMinute;
            }

            // getBotConfig() already ensures fresh load from disk
            for (GameConfig game : configManager.getBotConfig().games()) {
                if (!game.autoPost()) {
                    continue;
                }

                String postKey = game.gameName() + "-" + currentMinute;
                if (postedThisMinute.contains(postKey)) {
                    continue; // Đã post game này trong phút này rồi
                }

                if (currentMinute.equals(game.postTime())) {
                    postToChannel(game);
                    postedThisMinute.add(postKey);
                }
            }
        } catch (Exception e) {
            logger.error("Error in DailyScheduler tick: {}", e.getMessage(), e);
        }
    }

    /**
     * Gửi Embed vào channel của game, kèm ping role.
     */
    private void postToChannel(GameConfig game) {
        try {
            TextChannel channel = jda.getTextChannelById(game.channelId());
            if (channel == null) {
                logger.warn("Channel {} not found for game {}", game.channelId(), game.gameName());
                return;
            }

            String pingContent = "||<@&%s>||".formatted(game.roleId());

            channel.sendMessage(pingContent)
                    .setEmbeds(EmbedFactory.buildEmbed(game))
                    .queue(
                            success -> logger.info("Auto-posted Daily Custom for {} to channel #{}",
                                    game.gameName(), channel.getName()),
                            error -> logger.error("Failed to send message for {}: {}",
                                    game.gameName(), error.getMessage())
                    );
        } catch (Exception e) {
            logger.error("Error posting to channel for game {}: {}", game.gameName(), e.getMessage(), e);
        }
    }
}
