package unicorn.bot.dailycustombot.api;

import io.javalin.Javalin;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Server sử dụng Javalin.
 * Cung cấp REST API cho Google Apps Script gọi vào.
 * Bảo mật bằng API key trong header X-API-Key.
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    private final JDA jda;
    private final String apiKey;
    private Javalin app;

    public ApiServer(JDA jda) {
        this.jda = jda;
        this.apiKey = loadApiKey();
    }

    /**
     * Khởi động Javalin server.
     * Port đọc từ env var PORT (Railway tự inject), fallback 8080.
     */
    public void start() {
        int port = getPort();

        TeamService teamService = new TeamService(jda);
        TeamController teamController = new TeamController(teamService);

        app = Javalin.create(config -> {
            // Bật JSON parsing
            config.showJavalinBanner = false;
        });

        // ─── API Key Authentication Middleware ───
        app.before("/api/*", ctx -> {
            String requestKey = ctx.header("X-API-Key");
            if (apiKey == null || apiKey.isBlank()) {
                logger.warn("API_SECRET_KEY chưa được cấu hình! Cho phép tất cả request.");
                return; // Không có key → cho qua (dev mode)
            }

            if (requestKey == null || !requestKey.equals(apiKey)) {
                logger.warn("Unauthorized API request from {} — Invalid API key.", ctx.ip());
                throw new io.javalin.http.UnauthorizedResponse("Unauthorized: Invalid API key.");
            }
        });

        // ─── Routes ───
        app.post("/api/confirm-team", teamController::confirmTeam);

        // Health check endpoint (không cần API key)
        app.get("/health", ctx -> ctx.result("OK"));

        app.start(port);

        logger.info("═══════════════════════════════════════");
        logger.info("  API Server started on port {}", port);
        logger.info("  POST /api/confirm-team");
        logger.info("  GET  /health");
        logger.info("  API Key: {}", apiKey != null && !apiKey.isBlank() ? "CONFIGURED ✓" : "NOT SET ⚠️");
        logger.info("═══════════════════════════════════════");
    }

    /**
     * Dừng Javalin server.
     */
    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("API Server stopped.");
        }
    }

    /**
     * Lấy PORT từ env var (Railway tự inject), fallback 8080.
     */
    private int getPort() {
        String portStr = System.getenv("PORT");
        if (portStr != null && !portStr.isBlank()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT env var '{}', falling back to 8080.", portStr);
            }
        }
        return 8080;
    }

    /**
     * Đọc API key từ env var hoặc .env file.
     */
    private String loadApiKey() {
        // Thử environment variable trước
        String key = System.getenv("API_SECRET_KEY");
        if (key != null && !key.isBlank()) {
            logger.info("API_SECRET_KEY loaded from environment variable.");
            return key;
        }

        // Thử đọc từ .env
        try {
            java.nio.file.Path envFile = java.nio.file.Path.of(".env");
            if (java.nio.file.Files.exists(envFile)) {
                for (String line : java.nio.file.Files.readAllLines(envFile)) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isBlank()) continue;
                    if (line.startsWith("API_SECRET_KEY=")) {
                        String value = line.substring("API_SECRET_KEY=".length()).trim();
                        if (!value.isBlank()) {
                            logger.info("API_SECRET_KEY loaded from .env file.");
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read .env for API_SECRET_KEY: {}", e.getMessage());
        }

        logger.warn("API_SECRET_KEY not configured! API endpoints are unprotected.");
        return null;
    }
}
