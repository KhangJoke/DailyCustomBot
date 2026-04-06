package unicorn.bot.dailycustombot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.model.BotConfig;
import unicorn.bot.dailycustombot.model.EmbedData;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton quản lý đọc/ghi file config.json.
 * Thread-safe thông qua synchronized methods.
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final Path CONFIG_PATH = Path.of("config.json");
    private static ConfigManager instance;

    private final Gson gson;
    private BotConfig botConfig;

    private ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Đọc config từ file JSON. Nếu file chưa tồn tại, tạo file mặc định.
     */
    public synchronized void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                logger.info("config.json not found, creating default config...");
                botConfig = createDefaultConfig();
                saveConfig();
                return;
            }

            String json = Files.readString(CONFIG_PATH);
            botConfig = gson.fromJson(json, BotConfig.class);
            logger.debug("Loaded config.json with {} game(s).", botConfig.games().size());
        } catch (IOException e) {
            logger.error("Failed to read config.json: {}", e.getMessage(), e);
            botConfig = createDefaultConfig();
        }
    }

    /**
     * Ghi config hiện tại ra file JSON.
     */
    public synchronized void saveConfig() {
        try {
            String json = gson.toJson(botConfig);
            Files.writeString(CONFIG_PATH, json);
            logger.debug("config.json saved successfully.");
        } catch (IOException e) {
            logger.error("Failed to write config.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Lấy config của một game theo tên (case-insensitive).
     */
    public synchronized Optional<GameConfig> getGameConfig(String gameName) {
        return botConfig.games().stream()
                .filter(g -> g.gameName().equalsIgnoreCase(gameName))
                .findFirst();
    }

    /**
     * Lấy danh sách tên tất cả các game đã cấu hình.
     */
    public synchronized List<String> getGameNames() {
        return botConfig.games().stream()
                .map(GameConfig::gameName)
                .toList();
    }

    /**
     * Cập nhật config của một game. Nếu game chưa tồn tại, không làm gì.
     */
    public synchronized void updateGameConfig(GameConfig updatedGame) {
        List<GameConfig> updatedList = new ArrayList<>();
        for (GameConfig game : botConfig.games()) {
            if (game.gameName().equalsIgnoreCase(updatedGame.gameName())) {
                updatedList.add(updatedGame);
            } else {
                updatedList.add(game);
            }
        }
        botConfig = new BotConfig(updatedList);
        saveConfig();
    }

    /**
     * Thêm mới một config game. Nếu game đã tồn tại, sẽ bị ghi đè.
     */
    public synchronized void addGameConfig(GameConfig newGame) {
        List<GameConfig> updatedList = new ArrayList<>(botConfig.games());
        updatedList.removeIf(g -> g.gameName().equalsIgnoreCase(newGame.gameName()));
        updatedList.add(newGame);
        botConfig = new BotConfig(updatedList);
        saveConfig();
    }

    /**
     * Xóa một config game theo tên.
     * Trả về true nếu xóa thành công, false nếu không tìm thấy.
     */
    public synchronized boolean removeGameConfig(String gameName) {
        List<GameConfig> updatedList = new ArrayList<>(botConfig.games());
        boolean removed = updatedList.removeIf(g -> g.gameName().equalsIgnoreCase(gameName));
        if (removed) {
            botConfig = new BotConfig(updatedList);
            saveConfig();
        }
        return removed;
    }

    /**
     * Lấy toàn bộ BotConfig.
     */
    public synchronized BotConfig getBotConfig() {
        return botConfig;
    }

    /**
     * Lấy template Embed mặc định để dùng cho game mới.
     */
    public EmbedData getDefaultEmbedTemplate() {
        return new EmbedData(
                "1 Gói 2895 VP cho `Nhà Vô Địch`",
                "10 Chamber Coin / mạng hạ gục",
                """
                        Deathmatch 14 người
                        First to 40 kills
                        Không giới hạn đạn
                        Không giới hạn skill""",
                "All",
                "Pearl",
                "Tuỳ chọn",
                "17h ngày 21/03",
                "19h15 ngày 21/03",
                "Không giới hạn",
                "Không giới hạn",
                "https://forms.gle/example",
                "000000000000000000",
                "https://i.imgur.com/example.png",
                "https://i.imgur.com/example-icon.png"
        );
    }

    /**
     * Tạo config mặc định với dữ liệu mẫu Valorant.
     */
    private BotConfig createDefaultConfig() {
        EmbedData valorantEmbed = getDefaultEmbedTemplate();

        GameConfig valorant = new GameConfig(
                "Valorant",
                "000000000000000000",
                "000000000000000000",
                false,
                "18:00",
                valorantEmbed
        );

        return new BotConfig(List.of(valorant));
    }
}
