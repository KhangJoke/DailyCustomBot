package unicorn.bot.dailycustombot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.config.DatabaseManager;

import java.sql.*;

/**
 * Service quản lý bảng smp_registrations — lưu thông tin đăng ký whitelist.
 * Mỗi Discord user chỉ được đăng ký 1 lần (PRIMARY KEY = discord_user_id).
 */
public class SmpRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(SmpRegistrationService.class);

    /**
     * Lưu đăng ký mới vào DB.
     */
    public void saveRegistration(String discordUserId, String taikhoanNet, String tenIngame) {
        String sql = """
                INSERT INTO smp_registrations (discord_user_id, taikhoan_net, ten_ingame)
                VALUES (?, ?, ?)
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordUserId);
            ps.setString(2, taikhoanNet);
            ps.setString(3, tenIngame);
            ps.executeUpdate();
            logger.info("Đã lưu đăng ký: user={}, net={}, ingame={}", discordUserId, taikhoanNet, tenIngame);
        } catch (SQLException e) {
            logger.error("Lỗi lưu đăng ký: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể lưu đăng ký vào database.", e);
        }
    }

    /**
     * Tìm tên in-game hiện tại của user.
     *
     * @return Tên in-game hoặc null nếu chưa đăng ký
     */
    public String findIngameByUserId(String discordUserId) {
        String sql = "SELECT ten_ingame FROM smp_registrations WHERE discord_user_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("ten_ingame");
            }
            return null;
        } catch (SQLException e) {
            logger.error("Lỗi tìm đăng ký cho user {}: {}", discordUserId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Cập nhật tên in-game (khi user đổi tên nhân vật).
     *
     * @return true nếu update thành công, false nếu user chưa đăng ký
     */
    public boolean updateIngame(String discordUserId, String newIngame) {
        String sql = "UPDATE smp_registrations SET ten_ingame = ? WHERE discord_user_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newIngame);
            ps.setString(2, discordUserId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Đã cập nhật ingame cho user {}: {}", discordUserId, newIngame);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Lỗi cập nhật ingame cho user {}: {}", discordUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Xóa đăng ký của user.
     *
     * @return true nếu xóa thành công, false nếu user chưa đăng ký
     */
    public boolean deleteRegistration(String discordUserId) {
        String sql = "DELETE FROM smp_registrations WHERE discord_user_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordUserId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Đã xóa đăng ký của user {}", discordUserId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Lỗi xóa đăng ký cho user {}: {}", discordUserId, e.getMessage(), e);
            return false;
        }
    }
}
