package unicorn.bot.dailycustombot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.model.TicketConfig;
import unicorn.bot.dailycustombot.model.TicketType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton quản lý cấu hình ticket từ PostgreSQL database.
 * Thread-safe thông qua synchronized methods.
 * API public giữ nguyên so với phiên bản file.
 */
public class TicketConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(TicketConfigManager.class);
    private static TicketConfigManager instance;

    private TicketConfigManager() {
        // DatabaseManager đã tạo tables và insert default data
    }

    public static synchronized TicketConfigManager getInstance() {
        if (instance == null) {
            instance = new TicketConfigManager();
        }
        return instance;
    }

    /**
     * Lấy toàn bộ TicketConfig.
     */
    public synchronized TicketConfig getTicketConfig() {
        return loadFromDb();
    }

    /**
     * Lấy category ID nơi tạo ticket channels.
     */
    public synchronized String getTicketCategoryId() {
        return getConfigValue("ticket_category_id", "000000000000000000");
    }

    /**
     * Lấy staff role ID.
     */
    public synchronized String getStaffRoleId() {
        return getConfigValue("staff_role_id", "000000000000000000");
    }

    /**
     * Lấy log channel ID.
     */
    public synchronized String getLogChannelId() {
        return getConfigValue("log_channel_id", "000000000000000000");
    }

    /**
     * Lấy danh sách loại ticket.
     */
    public synchronized List<TicketType> getTicketTypes() {
        List<TicketType> types = new ArrayList<>();
        String sql = "SELECT * FROM ticket_types ORDER BY type_id";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                types.add(new TicketType(
                        rs.getString("type_id"),
                        rs.getString("emoji"),
                        rs.getString("label"),
                        rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to get ticket types: {}", e.getMessage(), e);
        }
        return types;
    }

    /**
     * Tìm loại ticket theo id.
     */
    public synchronized Optional<TicketType> getTicketType(String id) {
        String sql = "SELECT * FROM ticket_types WHERE LOWER(type_id) = LOWER(?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new TicketType(
                            rs.getString("type_id"),
                            rs.getString("emoji"),
                            rs.getString("label"),
                            rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get ticket type '{}': {}", id, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Lấy số ticket tiếp theo (tăng counter atomically và trả về).
     */
    public synchronized int getNextTicketNumber() {
        String sql = "UPDATE ticket_config SET ticket_counter = ticket_counter + 1 WHERE id = 1 RETURNING ticket_counter";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int next = rs.getInt("ticket_counter");
                logger.debug("Next ticket number: {}", next);
                return next;
            }
        } catch (SQLException e) {
            logger.error("Failed to get next ticket number: {}", e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Cập nhật các thông tin cấu hình chính (category, staff role, log channel).
     */
    public synchronized void updateConfig(String categoryId, String staffRoleId, String logChannelId) {
        String sql = """
                UPDATE ticket_config
                SET ticket_category_id = ?, staff_role_id = ?, log_channel_id = ?
                WHERE id = 1
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoryId);
            ps.setString(2, staffRoleId);
            ps.setString(3, logChannelId);
            ps.executeUpdate();
            logger.debug("Ticket config updated: category={}, staff={}, log={}",
                    categoryId, staffRoleId, logChannelId);
        } catch (SQLException e) {
            logger.error("Failed to update ticket config: {}", e.getMessage(), e);
        }
    }

    /**
     * Thêm loại ticket mới.
     * Trả về false nếu id đã tồn tại.
     */
    public synchronized boolean addTicketType(TicketType newType) {
        String sql = """
                INSERT INTO ticket_types (type_id, emoji, label, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (type_id) DO NOTHING
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newType.id());
            ps.setString(2, newType.emoji());
            ps.setString(3, newType.label());
            ps.setString(4, newType.description());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.debug("Added ticket type: {}", newType.id());
                return true;
            }
            return false; // Conflict — đã tồn tại
        } catch (SQLException e) {
            logger.error("Failed to add ticket type '{}': {}", newType.id(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Xóa loại ticket theo id.
     * Trả về true nếu xóa thành công, false nếu không tìm thấy.
     */
    public synchronized boolean removeTicketType(String id) {
        String sql = "DELETE FROM ticket_types WHERE LOWER(type_id) = LOWER(?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.debug("Removed ticket type: {}", id);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to remove ticket type '{}': {}", id, e.getMessage(), e);
            return false;
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Đọc một giá trị string từ ticket_config row.
     */
    private String getConfigValue(String column, String defaultValue) {
        String sql = "SELECT " + column + " FROM ticket_config WHERE id = 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to get config value '{}': {}", column, e.getMessage(), e);
        }
        return defaultValue;
    }

    /**
     * Load toàn bộ TicketConfig từ DB.
     */
    private TicketConfig loadFromDb() {
        String configSql = "SELECT * FROM ticket_config WHERE id = 1";
        String typesSql = "SELECT * FROM ticket_types ORDER BY type_id";

        String categoryId = "000000000000000000";
        String staffRoleId = "000000000000000000";
        String logChannelId = "000000000000000000";
        int counter = 0;
        List<TicketType> types = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(configSql)) {
                if (rs.next()) {
                    categoryId = rs.getString("ticket_category_id");
                    staffRoleId = rs.getString("staff_role_id");
                    logChannelId = rs.getString("log_channel_id");
                    counter = rs.getInt("ticket_counter");
                }
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(typesSql)) {
                while (rs.next()) {
                    types.add(new TicketType(
                            rs.getString("type_id"),
                            rs.getString("emoji"),
                            rs.getString("label"),
                            rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load ticket config from DB: {}", e.getMessage(), e);
        }

        return new TicketConfig(categoryId, staffRoleId, logChannelId, counter, types);
    }
}
