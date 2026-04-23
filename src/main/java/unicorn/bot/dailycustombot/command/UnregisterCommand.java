package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.service.RconService;
import unicorn.bot.dailycustombot.service.SmpRegistrationService;
import unicorn.bot.dailycustombot.config.EnvLoader;

/**
 * Handler cho lệnh /huydangky.
 * Xóa whitelist Minecraft và thu hồi Role Discord.
 *
 * Flow:
 * 1. Kiểm tra user đã đăng ký chưa (từ DB)
 * 2. RCON: whitelist remove {tên}
 * 3. Thu hồi Role Discord
 * 4. Xóa record khỏi DB
 */
public class UnregisterCommand {

    private static final Logger logger = LoggerFactory.getLogger(UnregisterCommand.class);

    private final RconService rconService = new RconService();
    private final SmpRegistrationService registrationService = new SmpRegistrationService();

    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        String userId = event.getUser().getId();
        String ingame = registrationService.findIngameByUserId(userId);

        if (ingame == null) {
            event.getHook().editOriginal(
                    "❌ Bạn chưa đăng ký Whitelist nên không có gì để hủy."
            ).queue();
            return;
        }

        logger.info("Lệnh /huydangky từ {} — xóa '{}'", event.getUser().getName(), ingame);

        try {
            // Bước 1: Xóa khỏi whitelist Minecraft
            rconService.removeWhitelist(ingame);
            logger.info("RCON: Đã xóa '{}' khỏi whitelist", ingame);

            // Bước 2: Thu hồi Role Discord
            removeDiscordRole(event);

            // Bước 3: Xóa record khỏi DB
            registrationService.deleteRegistration(userId);

            event.getHook().editOriginal(
                    "✅ Đã xóa nhân vật `" + ingame + "` khỏi Whitelist và thu hồi Role."
            ).queue();

        } catch (RconService.RconException e) {
            logger.error("Lỗi RCON khi xóa whitelist '{}': {}", ingame, e.getMessage(), e);
            event.getHook().editOriginal(
                    "⚠️ Không thể kết nối đến Minecraft Server. Vui lòng liên hệ Admin!"
            ).queue();
        } catch (Exception e) {
            logger.error("Lỗi hệ thống khi hủy đăng ký: {}", e.getMessage(), e);
            event.getHook().editOriginal(
                    "⚠️ Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ Admin!"
            ).queue();
        }
    }

    /**
     * Thu hồi Role SMP khỏi member.
     */
    private void removeDiscordRole(SlashCommandInteractionEvent event) {
        String roleSmpId = EnvLoader.get("ROLE_SMP_ID");
        if (roleSmpId == null || roleSmpId.isBlank()) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        Role role = guild.getRoleById(roleSmpId);
        if (role == null) return;

        Member member = event.getMember();
        if (member == null) return;

        guild.removeRoleFromMember(member, role).queue(
                success -> logger.info("Đã thu hồi Role '{}' từ '{}'",
                        role.getName(), member.getUser().getName()),
                error -> logger.error("Không thể thu hồi Role '{}' từ '{}': {}",
                        role.getName(), member.getUser().getName(), error.getMessage())
        );
    }
}
