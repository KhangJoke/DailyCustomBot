package unicorn.bot.dailycustombot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Singleton quản lý kết nối PostgreSQL database thông qua HikariCP connection
 * pool.
 * Tự động tạo tables khi khởi tạo.
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;

    private final HikariDataSource dataSource;

    private DatabaseManager(String databaseUrl) {
        HikariConfig config = new HikariConfig();

        // Xử lý định dạng postgresql://user:pass@host:port/db của Railway
        if (databaseUrl.startsWith("postgresql://") || databaseUrl.startsWith("postgres://")) {
            try {
                java.net.URI uri = new java.net.URI(databaseUrl);
                String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":"
                        + (uri.getPort() != -1 ? uri.getPort() : 5432) + uri.getPath();
                config.setJdbcUrl(jdbcUrl);

                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    config.setUsername(userInfo[0]);
                    if (userInfo.length > 1) {
                        config.setPassword(userInfo[1]);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to parse DATABASE_URL: {}", e.getMessage());
                config.setJdbcUrl(databaseUrl.startsWith("jdbc:") ? databaseUrl : "jdbc:" + databaseUrl);
            }
        } else {
            config.setJdbcUrl(databaseUrl.startsWith("jdbc:") ? databaseUrl : "jdbc:" + databaseUrl);
        }
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(300_000); // 5 minutes
        config.setConnectionTimeout(10_000); // 10 seconds
        config.setMaxLifetime(600_000); // 10 minutes
        config.setPoolName("BotDB-Pool");

        this.dataSource = new HikariDataSource(config);
        logger.info("Database connection pool initialized.");

        initTables();
    }

    /**
     * Khởi tạo singleton với DATABASE_URL.
     * Gọi 1 lần duy nhất trong main().
     */
    public static synchronized void init(String databaseUrl) {
        if (instance != null) {
            throw new IllegalStateException("DatabaseManager already initialized!");
        }
        instance = new DatabaseManager(databaseUrl);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DatabaseManager not initialized! Call init() first.");
        }
        return instance;
    }

    /**
     * Lấy connection từ pool.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Đóng connection pool (gọi khi shutdown).
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }

    /**
     * Tạo tất cả tables nếu chưa tồn tại.
     */
    private void initTables() {
        String createGameConfigs = """
                CREATE TABLE IF NOT EXISTS game_configs (
                    game_name           VARCHAR(100) PRIMARY KEY,
                    channel_id          VARCHAR(20) NOT NULL DEFAULT '000000000000000000',
                    role_id             VARCHAR(20) NOT NULL DEFAULT '000000000000000000',
                    auto_post           BOOLEAN DEFAULT false,
                    post_time           VARCHAR(10) DEFAULT '',
                    champion_prize      TEXT DEFAULT '',
                    kill_prize          TEXT DEFAULT '',
                    format_description  TEXT DEFAULT '',
                    gun                 VARCHAR(100) DEFAULT 'All',
                    map                 VARCHAR(100) DEFAULT '',
                    agent               VARCHAR(100) DEFAULT '',
                    register_deadline   VARCHAR(100) DEFAULT '',
                    match_time          VARCHAR(100) DEFAULT '',
                    rank_limit          VARCHAR(100) DEFAULT '',
                    age_limit           VARCHAR(100) DEFAULT '',
                    register_link       TEXT DEFAULT '',
                    support_channel_id  VARCHAR(20) DEFAULT '000000000000000000',
                    thumbnail_url       TEXT DEFAULT '',
                    footer_icon_url     TEXT DEFAULT ''
                );
                """;

        String createTicketConfig = """
                CREATE TABLE IF NOT EXISTS ticket_config (
                    id                  INT PRIMARY KEY DEFAULT 1,
                    ticket_category_id  VARCHAR(20) DEFAULT '000000000000000000',
                    staff_role_id       VARCHAR(20) DEFAULT '000000000000000000',
                    log_channel_id      VARCHAR(20) DEFAULT '000000000000000000',
                    ticket_counter      INT DEFAULT 0,
                    CONSTRAINT single_row CHECK (id = 1)
                );
                """;

        String createTicketTypes = """
                CREATE TABLE IF NOT EXISTS ticket_types (
                    type_id     VARCHAR(50) PRIMARY KEY,
                    emoji       VARCHAR(10) NOT NULL,
                    label       VARCHAR(100) NOT NULL,
                    description TEXT DEFAULT ''
                );
                """;

        // Insert default ticket_config row nếu chưa có
        String insertDefaultTicketConfig = """
                INSERT INTO ticket_config (id, ticket_category_id, staff_role_id, log_channel_id, ticket_counter)
                VALUES (1, '000000000000000000', '000000000000000000', '000000000000000000', 0)
                ON CONFLICT (id) DO NOTHING;
                """;

        // Insert default ticket_types nếu chưa có
        String insertDefaultTicketTypes = """
                INSERT INTO ticket_types (type_id, emoji, label, description) VALUES
                    ('support', '🛠️', 'Hỗ trợ', 'Yêu cầu hỗ trợ kỹ thuật, hướng dẫn'),
                    ('reward', '🎁', 'Nhận thưởng', 'Yêu cầu nhận phần thưởng coin, VP'),
                    ('download', '🎮', 'Tải game', 'Link tải game, hướng dẫn cài đặt'),
                    ('report', '⚠️', 'Tố cáo', 'Báo cáo vi phạm, gian lận'),
                    ('other', '💬', 'Khác', 'Vấn đề khác không thuộc danh mục trên')
                ON CONFLICT (type_id) DO NOTHING;
                """;

        String createMinigameSessions = """
                CREATE TABLE IF NOT EXISTS minigame_sessions (
                    message_id VARCHAR(50) PRIMARY KEY,
                    game_type VARCHAR(20) DEFAULT 'UNKNOWN',
                    video_url TEXT NOT NULL,
                    actual_rank VARCHAR(50) NOT NULL,
                    status VARCHAR(20) DEFAULT 'OPEN',
                    reward TEXT DEFAULT ''
                );
                """;

        String alterMinigameSessions = "ALTER TABLE minigame_sessions ADD COLUMN IF NOT EXISTS game_type VARCHAR(20) DEFAULT 'UNKNOWN';";
        String alterMinigameReward = "ALTER TABLE minigame_sessions ADD COLUMN IF NOT EXISTS reward TEXT DEFAULT '';";

        String createMinigameGuesses = """
                CREATE TABLE IF NOT EXISTS minigame_guesses (
                    message_id VARCHAR(50),
                    user_id VARCHAR(50),
                    guessed_rank VARCHAR(50),
                    PRIMARY KEY (message_id, user_id)
                );
                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createGameConfigs);
            stmt.execute(createTicketConfig);
            stmt.execute(createTicketTypes);
            stmt.execute(insertDefaultTicketConfig);
            stmt.execute(insertDefaultTicketTypes);
            stmt.execute(createMinigameSessions);
            stmt.execute(alterMinigameSessions);
            stmt.execute(alterMinigameReward);
            stmt.execute(createMinigameGuesses);
            logger.info("Database tables initialized successfully.");
        } catch (SQLException e) {
            logger.error("Failed to initialize database tables: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed!", e);
        }
    }

    /**
     * Load DATABASE_URL từ .env hoặc environment variable.
     */
    public static String loadDatabaseUrl() {
        // Thử tìm từ .env
        Path[] searchPaths = {
                Path.of(".env"),
                Path.of(System.getProperty("user.dir"), ".env"),
                getJarDirectory().resolve(".env"),
                getJarParentDirectory().resolve(".env")
        };

        for (Path envFile : searchPaths) {
            if (Files.exists(envFile)) {
                try {
                    List<String> lines = Files.readAllLines(envFile);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("#") || line.isBlank())
                            continue;
                        if (line.startsWith("DATABASE_URL=")) {
                            String url = line.substring("DATABASE_URL=".length()).trim();
                            if (!url.isBlank()) {
                                logger.info("DATABASE_URL loaded from .env file: {}", envFile.toAbsolutePath());
                                return url;
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Cannot read .env at {}: {}", envFile.toAbsolutePath(), e.getMessage());
                }
            }
        }

        // Fallback: environment variable
        String envUrl = System.getenv("DATABASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            logger.info("DATABASE_URL loaded from environment variable.");
            return envUrl;
        }

        return null;
    }

    private static Path getJarDirectory() {
        try {
            Path jarPath = Path.of(DatabaseManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return jarPath.getParent() != null ? jarPath.getParent() : Path.of(".");
        } catch (Exception e) {
            return Path.of(".");
        }
    }

    private static Path getJarParentDirectory() {
        Path jarDir = getJarDirectory();
        return jarDir.getParent() != null ? jarDir.getParent() : Path.of(".");
    }
}
