package unicorn.bot.dailycustombot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.ConfigManager;
import unicorn.bot.dailycustombot.config.DatabaseManager;
import unicorn.bot.dailycustombot.config.TicketConfigManager;
import unicorn.bot.dailycustombot.listener.SlashCommandListener;
import unicorn.bot.dailycustombot.listener.TicketButtonListener;
import unicorn.bot.dailycustombot.listener.TicketModalListener;
import unicorn.bot.dailycustombot.listener.TicketSelectMenuListener;
import unicorn.bot.dailycustombot.scheduler.DailyScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TimeZone;

/**
 * Entry point chính của Discord Bot Daily Custom.
 * Khởi tạo JDA, đăng ký Slash Commands, và khởi động Scheduler.
 */
public class DailyCustomBotApplication {

    private static final Logger logger = LoggerFactory.getLogger(DailyCustomBotApplication.class);

    public static void main(String[] args) {
        // Cố định Timezone để tránh lỗi "Asia/Saigon" với PostgreSQL JDBC
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        // Đọc token: ưu tiên file .env, fallback sang environment variable
        String token = loadToken();
        if (token == null || token.isBlank()) {
            logger.error("Bot token not found!");
            logger.error("   Option 1: Create .env file with: DISCORD_BOT_TOKEN=your_token");
            logger.error("   Option 2: Set environment variable DISCORD_BOT_TOKEN");
            System.exit(1);
        }

        // Khởi tạo Database
        String databaseUrl = DatabaseManager.loadDatabaseUrl();
        if (databaseUrl == null || databaseUrl.isBlank()) {
            logger.error("DATABASE_URL not found!");
            logger.error("   Option 1: Add DATABASE_URL=postgresql://... to .env file");
            logger.error("   Option 2: Set environment variable DATABASE_URL");
            System.exit(1);
        }
        DatabaseManager.init(databaseUrl);
        logger.info("DatabaseManager initialized.");

        // Khởi tạo ConfigManager
        ConfigManager.getInstance();
        logger.info("ConfigManager initialized.");

        // Khởi tạo TicketConfigManager
        TicketConfigManager.getInstance();
        logger.info("TicketConfigManager initialized.");

        try {
            // Build JDA instance
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(
                            new SlashCommandListener(),
                            new TicketButtonListener(),
                            new TicketSelectMenuListener(),
                            new TicketModalListener()
                    )
                    .build();

            // Chờ JDA sẵn sàng
            jda.awaitReady();
            logger.info("Bot connected to Discord! User: {}", jda.getSelfUser().getAsTag());

            // Đăng ký Slash Commands
            registerSlashCommands(jda);

            // Khởi động Scheduler
            DailyScheduler scheduler = new DailyScheduler(jda);
            scheduler.start();
            logger.info("DailyScheduler started.");

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down bot...");
                scheduler.shutdown();
                jda.shutdown();
                DatabaseManager.getInstance().shutdown();
                logger.info("Bot shutdown complete.");
            }));

            logger.info("═══════════════════════════════════════");
            logger.info("  Daily Custom Bot is running!");
            logger.info("  📋 Games: {}", ConfigManager.getInstance().getGameNames());
            logger.info("═══════════════════════════════════════");

        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for JDA to start!", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to start bot: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Đăng ký tất cả Slash Commands lên Discord.
     * Sử dụng game choices từ ConfigManager.
     */
    public static void registerSlashCommands(JDA jda) {
        List<String> gameNames = ConfigManager.getInstance().getGameNames();

        // Tạo option "game" dạng Choice cho tất cả commands
        OptionData gameOption = new OptionData(OptionType.STRING, "game", "Tên game", true);
        for (String name : gameNames) {
            gameOption.addChoice(name, name);
        }

        jda.updateCommands().addCommands(
                // /daily_update - Cập nhật config
                Commands.slash("daily_update", "Cập nhật thông tin giải đấu Daily Custom")
                        .addOptions(
                                new OptionData(OptionType.STRING, "game", "Tên game", true)
                                        .addChoices(gameNames.stream()
                                                .map(n -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(n, n))
                                                .toList()),
                                new OptionData(OptionType.STRING, "map", "Tên bản đồ", false),
                                new OptionData(OptionType.STRING, "gun", "Yêu cầu súng", false),
                                new OptionData(OptionType.STRING, "agent", "Yêu cầu nhân vật", false),
                                new OptionData(OptionType.STRING, "match_time", "Giờ thi đấu (VD: 19h15 ngày 21/03)", false),
                                new OptionData(OptionType.STRING, "register_deadline", "Hạn đăng ký (VD: 17h ngày 21/03)", false),
                                new OptionData(OptionType.STRING, "rank_limit", "Giới hạn rank", false),
                                new OptionData(OptionType.STRING, "age_limit", "Giới hạn tuổi", false),
                                new OptionData(OptionType.STRING, "register_link", "Link form đăng ký", false),
                                new OptionData(OptionType.STRING, "champion_prize", "Giải thưởng vô địch", false),
                                new OptionData(OptionType.STRING, "kill_prize", "Giải thưởng phụ", false),
                                new OptionData(OptionType.STRING, "format_description", "Mô tả thể thức", false)
                        ),

                // /daily_auto - Bật/tắt auto-post
                Commands.slash("daily_auto", "Bật/tắt tự động đăng bài Daily Custom")
                        .addOptions(
                                new OptionData(OptionType.STRING, "game", "Tên game", true)
                                        .addChoices(gameNames.stream()
                                                .map(n -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(n, n))
                                                .toList()),
                                new OptionData(OptionType.STRING, "toggle", "Bật/tắt", true)
                                        .addChoice("ON", "ON")
                                        .addChoice("OFF", "OFF"),
                                new OptionData(OptionType.STRING, "time", "Giờ đăng bài (HH:mm, VD: 18:00)", false)
                        ),

                // /daily_post_now - Đăng ngay
                Commands.slash("daily_post_now", "Đăng bài Daily Custom ngay lập tức")
                        .addOptions(
                                new OptionData(OptionType.STRING, "game", "Tên game", true)
                                        .addChoices(gameNames.stream()
                                                .map(n -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(n, n))
                                                .toList())
                        ),

                // /daily_view - Xem config
                Commands.slash("daily_view", "Xem cấu hình game hiện tại")
                        .addOptions(
                                new OptionData(OptionType.STRING, "game", "Tên game (để trống sẽ liệt kê tất cả)", false)
                                        .addChoices(gameNames.stream()
                                                .map(n -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(n, n))
                                                .toList())
                        ),

                // /daily_add - Thêm game mới
                Commands.slash("daily_add", "Thêm một tựa game mới vào hệ thống")
                        .addOptions(
                                new OptionData(OptionType.STRING, "game", "Tên game mới (VD: PUBG)", true),
                                new OptionData(OptionType.STRING, "channel_id", "ID kênh sẽ gửi bài", true),
                                new OptionData(OptionType.STRING, "role_id", "ID của Role cần ping (hoặc 0 nếu không ping)", true)
                        ),

                // /daily_remove - Xóa game
                Commands.slash("daily_remove", "Xóa một tựa game khỏi hệ thống")
                        .addOptions(
                                new OptionData(OptionType.STRING, "game", "Tên game cần xóa", true)
                                        .addChoices(gameNames.stream()
                                                .map(n -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(n, n))
                                                .toList())
                        ),

                // ===== TICKET SYSTEM COMMANDS =====

                // /ticket_setup - Thiết lập panel ticket
                Commands.slash("ticket_setup", "Thiết lập hệ thống ticket hỗ trợ")
                        .addOptions(
                                new OptionData(OptionType.STRING, "category_id", "ID Category để tạo channel ticket", true),
                                new OptionData(OptionType.STRING, "staff_role_id", "ID Role staff xử lý ticket", true),
                                new OptionData(OptionType.CHANNEL, "channel", "Channel để gửi panel ticket (mặc định: channel hiện tại)", false),
                                new OptionData(OptionType.STRING, "log_channel_id", "ID Channel để ghi log ticket", false)
                        ),

                // /ticket_add_type - Thêm loại ticket mới
                Commands.slash("ticket_add_type", "Thêm một loại ticket mới")
                        .addOptions(
                                new OptionData(OptionType.STRING, "id", "ID loại ticket (VD: bug_report)", true),
                                new OptionData(OptionType.STRING, "emoji", "Emoji đại diện (VD: 🐛)", true),
                                new OptionData(OptionType.STRING, "label", "Tên hiển thị (VD: Báo lỗi)", true),
                                new OptionData(OptionType.STRING, "description", "Mô tả ngắn", true)
                        ),

                // /ticket_remove_type - Xóa loại ticket
                Commands.slash("ticket_remove_type", "Xóa một loại ticket")
                        .addOptions(
                                new OptionData(OptionType.STRING, "id", "ID loại ticket cần xóa", true)
                        )
        ).queue(
                commands -> logger.info("Registered {} slash commands successfully!", commands.size()),
                error -> logger.error("Failed to register slash commands: {}", error.getMessage())
        );
    }

    /**
     * Đọc bot token theo thứ tự ưu tiên:
     * 1. File .env (DISCORD_BOT_TOKEN=xxx)
     * 2. Environment variable DISCORD_BOT_TOKEN
     */
    private static String loadToken() {
        // Thử tìm file .env ở nhiều vị trí
        Path jarDir = getJarDirectory();
        Path[] searchPaths = {
                Path.of(".env"),                              // 1. Thư mục hiện tại (CWD)
                Path.of(System.getProperty("user.dir"), ".env"),   // 2. User dir
                jarDir.resolve(".env"),                       // 3. Cùng thư mục chứa JAR (target/)
                jarDir.getParent() != null                    // 4. Thư mục cha của JAR (project root)
                        ? jarDir.getParent().resolve(".env")
                        : Path.of(".env")
        };

        for (Path envFile : searchPaths) {
            logger.debug("Searching for .env at: {}", envFile.toAbsolutePath());
            if (Files.exists(envFile)) {
                try {
                    List<String> lines = Files.readAllLines(envFile);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("#") || line.isBlank()) continue;
                        if (line.startsWith("DISCORD_BOT_TOKEN=")) {
                            String token = line.substring("DISCORD_BOT_TOKEN=".length()).trim();
                            if (!token.isBlank()) {
                                logger.info("Token loaded from .env file: {}", envFile.toAbsolutePath());
                                return token;
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Cannot read .env file at {}: {}", envFile.toAbsolutePath(), e.getMessage());
                }
            }
        }

        logger.warn("No .env file found. Searched locations:");
        for (Path p : searchPaths) {
            logger.warn("  - {}", p.toAbsolutePath());
        }

        // Fallback: environment variable
        String envToken = System.getenv("DISCORD_BOT_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            logger.info("Token loaded from environment variable.");
            return envToken;
        }

        return null;
    }

    /**
     * Lấy thư mục chứa file JAR đang chạy.
     */
    private static Path getJarDirectory() {
        try {
            Path jarPath = Path.of(DailyCustomBotApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return jarPath.getParent() != null ? jarPath.getParent() : Path.of(".");
        } catch (Exception e) {
            return Path.of(".");
        }
    }
}
