package unicorn.bot.dailycustombot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton quản lý database cho Mini Game Đoán Rank.
 */
public class GuessGameManager {

    private static final Logger logger = LoggerFactory.getLogger(GuessGameManager.class);
    private static GuessGameManager instance;

    private GuessGameManager() {
    }

    public static synchronized GuessGameManager getInstance() {
        if (instance == null) {
            instance = new GuessGameManager();
        }
        return instance;
    }

    /**
     * Tạo một session game mới.
     */
    public boolean createSession(String messageId, String gameType, String videoUrl, String actualRank) {
        String sql = "INSERT INTO minigame_sessions (message_id, game_type, video_url, actual_rank, status) VALUES (?, ?, ?, ?, 'OPEN')";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, gameType);
            ps.setString(3, videoUrl);
            ps.setString(4, actualRank);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to create minigame session {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    /**
     * Lấy status của session.
     */
    public Optional<String> getSessionStatus(String messageId) {
        String sql = "SELECT status FROM minigame_sessions WHERE message_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(rs.getString("status"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get session status {}: {}", messageId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Lấy actual_rank của session.
     */
    public Optional<String> getActualRank(String messageId) {
        String sql = "SELECT actual_rank FROM minigame_sessions WHERE message_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(rs.getString("actual_rank"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get actual rank {}: {}", messageId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Cập nhật trạng thái session (VD: CLOSED).
     */
    public boolean updateStatus(String messageId, String status) {
        String sql = "UPDATE minigame_sessions SET status = ? WHERE message_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update status {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    /**
     * Lấy lựa chọn trước đó của người chơi.
     */
    public String getPreviousGuess(String messageId, String userId) {
        String sql = "SELECT guessed_rank FROM minigame_guesses WHERE message_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("guessed_rank");
            }
        } catch (SQLException e) {
            logger.error("Failed to get previous guess: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Upsert lựa chọn của người chơi.
     */
    public void upsertGuess(String messageId, String userId, String guessedRank) {
        String sql = """
                INSERT INTO minigame_guesses (message_id, user_id, guessed_rank)
                VALUES (?, ?, ?)
                ON CONFLICT (message_id, user_id) DO UPDATE SET guessed_rank = EXCLUDED.guessed_rank
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, userId);
            ps.setString(3, guessedRank);
            ps.executeUpdate();
            logger.debug("User {} guessed {} for {}", userId, guessedRank, messageId);
        } catch (SQLException e) {
            logger.error("Failed to upsert guess: {}", e.getMessage());
        }
    }

    /**
     * Xóa lựa chọn của người chơi (có điều kiện check).
     */
    public void removeGuessExact(String messageId, String userId, String guessedRank) {
        String sql = "DELETE FROM minigame_guesses WHERE message_id = ? AND user_id = ? AND guessed_rank = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, userId);
            ps.setString(3, guessedRank);
            ps.executeUpdate();
            logger.debug("Removed guess {} for user {} on {}", guessedRank, userId, messageId);
        } catch (SQLException e) {
            logger.error("Failed to remove exact guess: {}", e.getMessage());
        }
    }

    /**
     * Lấy danh sách user_id của những người đoán trúng.
     */
    public List<String> getCorrectGuessers(String messageId, String actualRank) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT user_id FROM minigame_guesses WHERE message_id = ? AND guessed_rank = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, actualRank);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(rs.getString("user_id"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get correct guessers: {}", e.getMessage());
        }
        return list;
    }
}
