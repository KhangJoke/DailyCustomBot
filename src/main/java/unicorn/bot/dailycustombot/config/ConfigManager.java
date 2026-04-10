package unicorn.bot.dailycustombot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.model.BotConfig;
import unicorn.bot.dailycustombot.model.EmbedData;
import unicorn.bot.dailycustombot.model.GameConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton quản lý cấu hình game từ PostgreSQL database.
 * Thread-safe thông qua synchronized methods.
 * API public giữ nguyên so với phiên bản file để không ảnh hưởng commands.
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static ConfigManager instance;

    private ConfigManager() {
        // Không cần init gì — DatabaseManager đã tạo tables
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Lấy config của một game theo tên (case-insensitive).
     */
    public synchronized Optional<GameConfig> getGameConfig(String gameName) {
        String sql = "SELECT * FROM game_configs WHERE LOWER(game_name) = LOWER(?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gameName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGameConfig(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get game config for '{}': {}", gameName, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Lấy danh sách tên tất cả các game đã cấu hình.
     */
    public synchronized List<String> getGameNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT game_name FROM game_configs ORDER BY game_name";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("game_name"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get game names: {}", e.getMessage(), e);
        }
        return names;
    }

    /**
     * Lấy toàn bộ BotConfig.
     */
    public synchronized BotConfig getBotConfig() {
        List<GameConfig> games = new ArrayList<>();
        String sql = "SELECT * FROM game_configs ORDER BY game_name";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                games.add(mapResultSetToGameConfig(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to get bot config: {}", e.getMessage(), e);
        }
        return new BotConfig(games);
    }

    /**
     * Cập nhật config của một game. Nếu game chưa tồn tại, không làm gì.
     */
    public synchronized void updateGameConfig(GameConfig g) {
        String sql = """
                UPDATE game_configs SET
                    channel_id = ?, role_id = ?, auto_post = ?, post_time = ?,
                    champion_prize = ?, kill_prize = ?, format_description = ?,
                    detail1 = ?, map = ?, detail2 = ?,
                    register_deadline = ?, match_time = ?, rank_limit = ?, age_limit = ?,
                    register_link = ?, support_channel_id = ?, thumbnail_url = ?, footer_icon_url = ?
                WHERE LOWER(game_name) = LOWER(?)
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            EmbedData e = g.embedData();
            ps.setString(1, g.channelId());
            ps.setString(2, g.roleId());
            ps.setBoolean(3, g.autoPost());
            ps.setString(4, g.postTime());
            ps.setString(5, e.championPrize());
            ps.setString(6, e.killPrize());
            ps.setString(7, e.formatDescription());
            ps.setString(8, e.detail1());
            ps.setString(9, e.map());
            ps.setString(10, e.detail2());
            ps.setString(11, e.registerDeadline());
            ps.setString(12, e.matchTime());
            ps.setString(13, e.rankLimit());
            ps.setString(14, e.ageLimit());
            ps.setString(15, e.registerLink());
            ps.setString(16, e.supportChannelId());
            ps.setString(17, e.thumbnailUrl());
            ps.setString(18, e.footerIconUrl());
            ps.setString(19, g.gameName());

            int rows = ps.executeUpdate();
            logger.debug("Updated game config '{}': {} row(s) affected.", g.gameName(), rows);
        } catch (SQLException ex) {
            logger.error("Failed to update game config '{}': {}", g.gameName(), ex.getMessage(), ex);
        }
    }

    /**
     * Thêm mới một config game. Nếu game đã tồn tại, sẽ bị ghi đè (UPSERT).
     */
    public synchronized void addGameConfig(GameConfig g) {
        String sql = """
                INSERT INTO game_configs (
                    game_name, channel_id, role_id, auto_post, post_time,
                    champion_prize, kill_prize, format_description,
                    detail1, map, detail2,
                    register_deadline, match_time, rank_limit, age_limit,
                    register_link, support_channel_id, thumbnail_url, footer_icon_url
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (game_name) DO UPDATE SET
                    channel_id = EXCLUDED.channel_id, role_id = EXCLUDED.role_id,
                    auto_post = EXCLUDED.auto_post, post_time = EXCLUDED.post_time,
                    champion_prize = EXCLUDED.champion_prize, kill_prize = EXCLUDED.kill_prize,
                    format_description = EXCLUDED.format_description,
                    detail1 = EXCLUDED.detail1, map = EXCLUDED.map, detail2 = EXCLUDED.detail2,
                    register_deadline = EXCLUDED.register_deadline, match_time = EXCLUDED.match_time,
                    rank_limit = EXCLUDED.rank_limit, age_limit = EXCLUDED.age_limit,
                    register_link = EXCLUDED.register_link, support_channel_id = EXCLUDED.support_channel_id,
                    thumbnail_url = EXCLUDED.thumbnail_url, footer_icon_url = EXCLUDED.footer_icon_url
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            EmbedData e = g.embedData();
            ps.setString(1, g.gameName());
            ps.setString(2, g.channelId());
            ps.setString(3, g.roleId());
            ps.setBoolean(4, g.autoPost());
            ps.setString(5, g.postTime());
            ps.setString(6, e.championPrize());
            ps.setString(7, e.killPrize());
            ps.setString(8, e.formatDescription());
            ps.setString(9, e.detail1());
            ps.setString(10, e.map());
            ps.setString(11, e.detail2());
            ps.setString(12, e.registerDeadline());
            ps.setString(13, e.matchTime());
            ps.setString(14, e.rankLimit());
            ps.setString(15, e.ageLimit());
            ps.setString(16, e.registerLink());
            ps.setString(17, e.supportChannelId());
            ps.setString(18, e.thumbnailUrl());
            ps.setString(19, e.footerIconUrl());

            ps.executeUpdate();
            logger.debug("Added/upserted game config: {}", g.gameName());
        } catch (SQLException ex) {
            logger.error("Failed to add game config '{}': {}", g.gameName(), ex.getMessage(), ex);
        }
    }

    /**
     * Xóa một config game theo tên.
     * Trả về true nếu xóa thành công, false nếu không tìm thấy.
     */
    public synchronized boolean removeGameConfig(String gameName) {
        String sql = "DELETE FROM game_configs WHERE LOWER(game_name) = LOWER(?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gameName);
            int rows = ps.executeUpdate();
            logger.debug("Removed game config '{}': {} row(s) deleted.", gameName, rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("Failed to remove game config '{}': {}", gameName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Lấy template Embed mặc định theo tên game.
     * Mỗi game có nội dung và label phù hợp riêng.
     *
     * @param gameName Tên game (case-insensitive): "Valorant", "LoL", v.v.
     * @return EmbedData với nội dung mặc định tương ứng
     */
    public EmbedData getDefaultEmbedTemplate(String gameName) {
        String normalized = gameName.toLowerCase().trim();
        return switch (normalized) {
            case "valorant", "val" -> getValorantDefault();
            case "lol", "league of legends", "lmht", "liên minh" -> getLoLDefault();
            default -> getGenericDefault(gameName);
        };
    }

    /**
     * Template mặc định cho Valorant.
     * detail1 = Súng, detail2 = Agent
     */
    private EmbedData getValorantDefault() {
        return new EmbedData(
                "1 Gói 2895 VP cho `Nhà Vô Địch`",
                "10 Chamber Coin / mạng hạ gục",
                """
                        Deathmatch 14 người
                        First to 40 kills
                        Không giới hạn đạn
                        Không giới hạn skill""",
                "All",           // detail1 → Súng
                "Pearl",         // map
                "Tuỳ chọn",      // detail2 → Agent
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
     * Template mặc định cho League of Legends.
     * detail1 = Chế độ, detail2 = Tướng
     */
    private EmbedData getLoLDefault() {
        return new EmbedData(
                "1 Skin Legacy cho `Nhà Vô Địch`",
                "500 Blue Essence / mạng hạ gục",
                """
                        Custom Game 5v5
                        Summoner's Rift
                        Draft Pick""",
                "Draft Pick",            // detail1 → Chế độ
                "Summoner's Rift",       // map
                "Tuỳ chọn",              // detail2 → Tướng
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
     * Template mặc định cho game bất kỳ (generic).
     * detail1 = Chi tiết 1, detail2 = Chi tiết 2
     */
    private EmbedData getGenericDefault(String gameName) {
        return new EmbedData(
                "Giải thưởng cho `Nhà Vô Địch`",
                "Giải thưởng phụ",
                "Chưa cấu hình — dùng /daily_update để cập nhật",
                "Chưa cấu hình",     // detail1
                "Chưa cấu hình",     // map
                "Chưa cấu hình",     // detail2
                "Chưa cấu hình",
                "Chưa cấu hình",
                "Không giới hạn",
                "Không giới hạn",
                "https://forms.gle/example",
                "000000000000000000",
                "https://i.imgur.com/example.png",
                "https://i.imgur.com/example-icon.png"
        );
    }

    /**
     * Map ResultSet row thành GameConfig record.
     */
    private GameConfig mapResultSetToGameConfig(ResultSet rs) throws SQLException {
        EmbedData embedData = new EmbedData(
                rs.getString("champion_prize"),
                rs.getString("kill_prize"),
                rs.getString("format_description"),
                rs.getString("detail1"),
                rs.getString("map"),
                rs.getString("detail2"),
                rs.getString("register_deadline"),
                rs.getString("match_time"),
                rs.getString("rank_limit"),
                rs.getString("age_limit"),
                rs.getString("register_link"),
                rs.getString("support_channel_id"),
                rs.getString("thumbnail_url"),
                rs.getString("footer_icon_url")
        );

        return new GameConfig(
                rs.getString("game_name"),
                rs.getString("channel_id"),
                rs.getString("role_id"),
                rs.getBoolean("auto_post"),
                rs.getString("post_time"),
                embedData
        );
    }
}
