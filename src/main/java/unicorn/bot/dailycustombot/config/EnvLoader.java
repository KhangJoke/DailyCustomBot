package unicorn.bot.dailycustombot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiện ích đọc biến môi trường — cùng pattern với loadToken() và loadDatabaseUrl().
 * Ưu tiên: .env file → System.getenv()
 *
 * Gọi EnvLoader.init() một lần duy nhất trong main().
 * Sau đó dùng EnvLoader.get("KEY") thay cho System.getenv().
 */
public class EnvLoader {

    private static final Logger logger = LoggerFactory.getLogger(EnvLoader.class);
    private static final Map<String, String> envMap = new HashMap<>();
    private static boolean initialized = false;

    /**
     * Đọc tất cả KEY=VALUE từ file .env (cùng logic tìm file như loadToken).
     */
    public static synchronized void init() {
        if (initialized) return;

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
                        if (line.isBlank() || line.startsWith("#")) continue;
                        int eqIndex = line.indexOf('=');
                        if (eqIndex > 0) {
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            envMap.put(key, value);
                        }
                    }
                    logger.info("EnvLoader: Đã load {} biến từ {}", envMap.size(), envFile.toAbsolutePath());
                    break;
                } catch (IOException e) {
                    logger.warn("EnvLoader: Không đọc được {}: {}", envFile.toAbsolutePath(), e.getMessage());
                }
            }
        }
        initialized = true;
    }

    /**
     * Lấy giá trị biến môi trường.
     * Ưu tiên: .env file → System.getenv()
     */
    public static String get(String key) {
        String value = envMap.get(key);
        if (value != null && !value.isBlank()) return value;
        return System.getenv(key);
    }

    private static Path getJarDirectory() {
        try {
            Path jarPath = Path.of(EnvLoader.class.getProtectionDomain()
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
