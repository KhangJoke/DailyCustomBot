package unicorn.bot.dailycustombot.config;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton quản lý phân quyền role-based.
 * - Admin (Permission.ADMINISTRATOR) luôn toàn quyền.
 * - Các role khác cần được gán quyền cho từng nhóm lệnh (command group).
 * - Cache in-memory để tránh query DB mỗi lần check.
 */
public class PermissionManager {

    private static final Logger logger = LoggerFactory.getLogger(PermissionManager.class);
    private static PermissionManager instance;

    /** Các command group hợp lệ */
    public static final List<String> VALID_GROUPS = List.of("daily", "ticket", "minigame");

    /** Nhóm chỉ dành cho admin, không cho phép gán role */
    public static final String PERMISSION_GROUP = "permission";

    /**
     * Cache: guildId -> (commandGroup -> Set<roleId>)
     */
    private final Map<String, Map<String, Set<String>>> cache = new ConcurrentHashMap<>();

    private PermissionManager() {
        loadAllPermissions();
    }

    public static synchronized PermissionManager getInstance() {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }

    /**
     * Kiểm tra xem member có quyền sử dụng nhóm lệnh hay không.
     * 1. Admin (ADMINISTRATOR) -> luôn true
     * 2. Nhóm "permission" -> chỉ admin
     * 3. Check role trong database/cache
     */
    public boolean hasAccess(Member member, String commandGroup) {
        if (member == null) return false;

        // Admin luôn toàn quyền
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }

        // Nhóm permission chỉ dành cho Admin
        if (PERMISSION_GROUP.equals(commandGroup)) {
            return false;
        }

        String guildId = member.getGuild().getId();
        Map<String, Set<String>> guildPerms = cache.get(guildId);
        if (guildPerms == null) {
            return false;
        }

        Set<String> allowedRoles = guildPerms.get(commandGroup);
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return false;
        }

        // Kiểm tra member có role nào trong danh sách được phép không
        for (Role role : member.getRoles()) {
            if (allowedRoles.contains(role.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Thêm quyền cho role trong guild.
     * @return true nếu thêm thành công (chưa tồn tại), false nếu đã có
     */
    public boolean addRolePermission(String guildId, String roleId, String commandGroup) {
        String sql = """
                INSERT INTO role_permissions (guild_id, role_id, command_group)
                VALUES (?, ?, ?)
                ON CONFLICT (guild_id, role_id, command_group) DO NOTHING;
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, roleId);
            ps.setString(3, commandGroup);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                // Cập nhật cache
                cache.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(commandGroup, k -> ConcurrentHashMap.newKeySet())
                        .add(roleId);
                logger.info("Added permission: guild={}, role={}, group={}", guildId, roleId, commandGroup);
                return true;
            }
            return false; // Đã tồn tại
        } catch (SQLException e) {
            logger.error("Failed to add role permission: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Xóa quyền của role trong guild.
     * @return true nếu xóa thành công, false nếu không tồn tại
     */
    public boolean removeRolePermission(String guildId, String roleId, String commandGroup) {
        String sql = "DELETE FROM role_permissions WHERE guild_id = ? AND role_id = ? AND command_group = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, roleId);
            ps.setString(3, commandGroup);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                // Cập nhật cache
                Map<String, Set<String>> guildPerms = cache.get(guildId);
                if (guildPerms != null) {
                    Set<String> roles = guildPerms.get(commandGroup);
                    if (roles != null) {
                        roles.remove(roleId);
                    }
                }
                logger.info("Removed permission: guild={}, role={}, group={}", guildId, roleId, commandGroup);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to remove role permission: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Lấy tất cả quyền trong guild.
     * @return Map<commandGroup, Set<roleId>>
     */
    public Map<String, Set<String>> getGuildPermissions(String guildId) {
        Map<String, Set<String>> guildPerms = cache.get(guildId);
        if (guildPerms == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(guildPerms);
    }

    /**
     * Load tất cả permissions từ DB vào cache.
     */
    private void loadAllPermissions() {
        String sql = "SELECT guild_id, role_id, command_group FROM role_permissions";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                String guildId = rs.getString("guild_id");
                String roleId = rs.getString("role_id");
                String commandGroup = rs.getString("command_group");

                cache.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(commandGroup, k -> ConcurrentHashMap.newKeySet())
                        .add(roleId);
                count++;
            }

            logger.info("Loaded {} role permissions from database.", count);
        } catch (SQLException e) {
            logger.error("Failed to load role permissions: {}", e.getMessage(), e);
        }
    }
}
