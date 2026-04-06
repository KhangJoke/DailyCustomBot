package unicorn.bot.dailycustombot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.model.TicketConfig;
import unicorn.bot.dailycustombot.model.TicketType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton quản lý đọc/ghi file ticket-config.json.
 * Thread-safe thông qua synchronized methods.
 */
public class TicketConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(TicketConfigManager.class);
    private static final Path CONFIG_PATH = Path.of("ticket-config.json");
    private static TicketConfigManager instance;

    private final Gson gson;
    private TicketConfig ticketConfig;

    private TicketConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadConfig();
    }

    public static synchronized TicketConfigManager getInstance() {
        if (instance == null) {
            instance = new TicketConfigManager();
        }
        return instance;
    }

    /**
     * Đọc config từ file JSON. Nếu file chưa tồn tại, tạo file mặc định.
     */
    public synchronized void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                logger.info("ticket-config.json not found, creating default config...");
                ticketConfig = createDefaultConfig();
                saveConfig();
                return;
            }

            String json = Files.readString(CONFIG_PATH);
            ticketConfig = gson.fromJson(json, TicketConfig.class);
            logger.debug("Loaded ticket-config.json with {} ticket type(s).", ticketConfig.ticketTypes().size());
        } catch (IOException e) {
            logger.error("Failed to read ticket-config.json: {}", e.getMessage(), e);
            ticketConfig = createDefaultConfig();
        }
    }

    /**
     * Ghi config hiện tại ra file JSON.
     */
    public synchronized void saveConfig() {
        try {
            String json = gson.toJson(ticketConfig);
            Files.writeString(CONFIG_PATH, json);
            logger.debug("ticket-config.json saved successfully.");
        } catch (IOException e) {
            logger.error("Failed to write ticket-config.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Lấy toàn bộ TicketConfig.
     */
    public synchronized TicketConfig getTicketConfig() {
        return ticketConfig;
    }

    /**
     * Lấy category ID nơi tạo ticket channels.
     */
    public synchronized String getTicketCategoryId() {
        return ticketConfig.ticketCategoryId();
    }

    /**
     * Lấy staff role ID.
     */
    public synchronized String getStaffRoleId() {
        return ticketConfig.staffRoleId();
    }

    /**
     * Lấy log channel ID.
     */
    public synchronized String getLogChannelId() {
        return ticketConfig.logChannelId();
    }

    /**
     * Lấy danh sách loại ticket.
     */
    public synchronized List<TicketType> getTicketTypes() {
        return ticketConfig.ticketTypes();
    }

    /**
     * Tìm loại ticket theo id.
     */
    public synchronized Optional<TicketType> getTicketType(String id) {
        return ticketConfig.ticketTypes().stream()
                .filter(t -> t.id().equalsIgnoreCase(id))
                .findFirst();
    }

    /**
     * Lấy số ticket tiếp theo (tăng counter và lưu).
     */
    public synchronized int getNextTicketNumber() {
        int next = ticketConfig.ticketCounter() + 1;
        ticketConfig = new TicketConfig(
                ticketConfig.ticketCategoryId(),
                ticketConfig.staffRoleId(),
                ticketConfig.logChannelId(),
                next,
                ticketConfig.ticketTypes()
        );
        saveConfig();
        return next;
    }

    /**
     * Cập nhật các thông tin cấu hình chính (category, staff role, log channel).
     */
    public synchronized void updateConfig(String categoryId, String staffRoleId, String logChannelId) {
        ticketConfig = new TicketConfig(
                categoryId,
                staffRoleId,
                logChannelId,
                ticketConfig.ticketCounter(),
                ticketConfig.ticketTypes()
        );
        saveConfig();
    }

    /**
     * Thêm loại ticket mới.
     * Trả về false nếu id đã tồn tại.
     */
    public synchronized boolean addTicketType(TicketType newType) {
        boolean exists = ticketConfig.ticketTypes().stream()
                .anyMatch(t -> t.id().equalsIgnoreCase(newType.id()));
        if (exists) {
            return false;
        }

        List<TicketType> updatedList = new ArrayList<>(ticketConfig.ticketTypes());
        updatedList.add(newType);
        ticketConfig = new TicketConfig(
                ticketConfig.ticketCategoryId(),
                ticketConfig.staffRoleId(),
                ticketConfig.logChannelId(),
                ticketConfig.ticketCounter(),
                updatedList
        );
        saveConfig();
        return true;
    }

    /**
     * Xóa loại ticket theo id.
     * Trả về true nếu xóa thành công, false nếu không tìm thấy.
     */
    public synchronized boolean removeTicketType(String id) {
        List<TicketType> updatedList = new ArrayList<>(ticketConfig.ticketTypes());
        boolean removed = updatedList.removeIf(t -> t.id().equalsIgnoreCase(id));
        if (removed) {
            ticketConfig = new TicketConfig(
                    ticketConfig.ticketCategoryId(),
                    ticketConfig.staffRoleId(),
                    ticketConfig.logChannelId(),
                    ticketConfig.ticketCounter(),
                    updatedList
            );
            saveConfig();
        }
        return removed;
    }

    /**
     * Tạo config mặc định với 5 loại ticket.
     */
    private TicketConfig createDefaultConfig() {
        List<TicketType> defaultTypes = List.of(
                new TicketType("support", "🛠️", "Hỗ trợ", "Yêu cầu hỗ trợ kỹ thuật, hướng dẫn"),
                new TicketType("reward", "🎁", "Nhận thưởng", "Yêu cầu nhận phần thưởng coin, VP"),
                new TicketType("download", "🎮", "Tải game", "Link tải game, hướng dẫn cài đặt"),
                new TicketType("report", "⚠️", "Tố cáo", "Báo cáo vi phạm, gian lận"),
                new TicketType("other", "💬", "Khác", "Vấn đề khác không thuộc danh mục trên")
        );

        return new TicketConfig(
                "000000000000000000",
                "000000000000000000",
                "000000000000000000",
                0,
                defaultTypes
        );
    }
}
