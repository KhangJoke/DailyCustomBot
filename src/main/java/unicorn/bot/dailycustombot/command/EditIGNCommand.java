package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.service.RconService;
import unicorn.bot.dailycustombot.service.SmpRegistrationService;
import unicorn.bot.dailycustombot.config.EnvLoader;

/**
 * Handler cho lệnh /suaten.
 * Đổi tên nhân vật Minecraft trên whitelist.
 *
 * Flow:
 * 1. Kiểm tra user đã đăng ký chưa (từ DB)
 * 2. RCON: whitelist remove {tên cũ}
 * 3. RCON: whitelist add {tên mới}
 * 4. Cập nhật DB
 */
public class EditIGNCommand {

    private static final Logger logger = LoggerFactory.getLogger(EditIGNCommand.class);

    private final RconService rconService = new RconService();
    private final SmpRegistrationService registrationService = new SmpRegistrationService();

    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        String newIngame = event.getOption("ten_ingame_moi", OptionMapping::getAsString);
        if (newIngame == null || newIngame.isBlank()) {
            event.getHook().editOriginal("❌ Vui lòng cung cấp tên nhân vật mới!").queue();
            return;
        }

        // Validate: tên in-game phải viết thường hoàn toàn
        if (!newIngame.equals(newIngame.toLowerCase())) {
            event.getHook().editOriginal(
                    "❌ Tên nhân vật phải được nhập bằng **chữ thường (lowercase)** hoàn toàn!\n"
                            + "⚠️ **Lưu ý:** Server yêu cầu tên Minecraft phải viết thường để whitelist hoạt động chính xác.\n"
                            + "📝 Ví dụ: `" + newIngame.toLowerCase() + "` thay vì `" + newIngame + "`"
            ).queue();
            return;
        }

        String userId = event.getUser().getId();
        String oldIngame = registrationService.findIngameByUserId(userId);

        if (oldIngame == null) {
            event.getHook().editOriginal(
                    "❌ Bạn chưa đăng ký Whitelist! Vui lòng dùng lệnh `/dangky` trước."
            ).queue();
            return;
        }

        if (oldIngame.equalsIgnoreCase(newIngame)) {
            event.getHook().editOriginal(
                    "ℹ️ Tên nhân vật mới trùng với tên hiện tại (`" + oldIngame + "`)."
            ).queue();
            return;
        }

        logger.info("Lệnh /suaten từ {} — đổi '{}' → '{}'",
                event.getUser().getName(), oldIngame, newIngame);

        try {
            // Bước 1: Xóa tên cũ khỏi whitelist
            rconService.removeWhitelist(oldIngame);
            logger.info("RCON: Đã xóa '{}' khỏi whitelist", oldIngame);

            // Bước 2: Thêm tên mới vào whitelist
            rconService.addWhitelist(newIngame);
            logger.info("RCON: Đã thêm '{}' vào whitelist", newIngame);

            // Bước 3: Cập nhật DB
            registrationService.updateIngame(userId, newIngame);

            event.getHook().editOriginal(
                    "✅ Đã đổi tên nhân vật từ `" + oldIngame + "` → `" + newIngame + "` trên Whitelist."
            ).queue();

        } catch (RconService.RconException e) {
            logger.error("Lỗi RCON khi đổi tên '{}' → '{}': {}", oldIngame, newIngame, e.getMessage(), e);
            event.getHook().editOriginal(
                    "⚠️ Không thể kết nối đến Minecraft Server. Vui lòng liên hệ Admin!"
            ).queue();
        } catch (Exception e) {
            logger.error("Lỗi hệ thống khi đổi tên: {}", e.getMessage(), e);
            event.getHook().editOriginal(
                    "⚠️ Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ Admin!"
            ).queue();
        }
    }
}
