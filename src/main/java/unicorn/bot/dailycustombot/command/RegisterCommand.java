package unicorn.bot.dailycustombot.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unicorn.bot.dailycustombot.service.NetApiService;
import unicorn.bot.dailycustombot.service.RconService;
import unicorn.bot.dailycustombot.service.SmpRegistrationService;
import unicorn.bot.dailycustombot.config.EnvLoader;

/**
 * Handler cho lệnh /dangky.
 * Xác thực tài khoản Net Cafe qua API bên ngoài,
 * sau đó cấp Role Discord và thêm nhân vật vào Whitelist Minecraft qua RCON.
 *
 * Flow:
 * 1. Kiểm tra user đã đăng ký chưa (từ DB)
 * 2. Nhận options: taikhoan_net, ten_ingame
 * 3. Gọi API xác thực → parse data.exists
 * 4. Nếu exists = true → Cấp Role + RCON whitelist + Lưu DB
 * 5. Nếu exists = false → Thông báo lỗi
 */
public class RegisterCommand {

    private static final Logger logger = LoggerFactory.getLogger(RegisterCommand.class);

    private final NetApiService netApiService = new NetApiService();
    private final RconService rconService = new RconService();
    private final SmpRegistrationService registrationService = new SmpRegistrationService();

    public void handle(SlashCommandInteractionEvent event) {
        // Acknowledge ngay lập tức để tránh timeout 3 giây của Discord
        event.deferReply().setEphemeral(true).queue();

        String taiKhoanNet = event.getOption("taikhoan_net", OptionMapping::getAsString);
        String tenIngame = event.getOption("ten_ingame", OptionMapping::getAsString);

        if (taiKhoanNet == null || tenIngame == null) {
            event.getHook().editOriginal("❌ Vui lòng cung cấp đầy đủ tài khoản Net và tên in-game!").queue();
            return;
        }

        // Kiểm tra user đã đăng ký chưa
        String existingIngame = registrationService.findIngameByUserId(event.getUser().getId());
        if (existingIngame != null) {
            event.getHook().editOriginal(
                    "❌ Bạn đã đăng ký với tên nhân vật `" + existingIngame + "` rồi!\n"
                            + "• Dùng `/suaten` để đổi tên nhân vật.\n"
                            + "• Dùng `/huydangky` để hủy đăng ký."
            ).queue();
            return;
        }

        logger.info("Lệnh /dangky từ {} — taikhoan_net={}, ten_ingame={}",
                event.getUser().getName(), taiKhoanNet, tenIngame);

        // Gọi API xác thực tài khoản Net Cafe (bất đồng bộ)
        netApiService.verifyAccount(taiKhoanNet).thenAccept(isValid -> {
            if (!isValid) {
                // data.exists = false hoặc lỗi API → Tài khoản không hợp lệ
                event.getHook().editOriginal(
                        "❌ Tài khoản Net `" + taiKhoanNet + "` không tồn tại. Vui lòng kiểm tra lại!"
                ).queue();
                return;
            }

            // === data.exists = true — Xác thực thành công ===
            try {
                // Hành động A: Cấp Role Discord (ROLE_SMP_ID)
                assignDiscordRole(event);

                // Hành động B: Thêm Whitelist Minecraft qua RCON
                String rconResponse = rconService.addWhitelist(tenIngame);
                logger.info("RCON whitelist response cho '{}': {}", tenIngame, rconResponse);

                // Hành động C: Lưu đăng ký vào DB
                registrationService.saveRegistration(
                        event.getUser().getId(), taiKhoanNet, tenIngame);

                // Phản hồi thành công cho user
                event.getHook().editOriginal(
                        "✅ Xác thực thành công! Đã thêm nhân vật `"
                                + tenIngame + "` vào Whitelist."
                ).queue();

            } catch (RconService.RconException e) {
                logger.error("Lỗi RCON khi whitelist '{}': {}", tenIngame, e.getMessage(), e);
                event.getHook().editOriginal(
                        "⚠️ Xác thực tài khoản thành công nhưng không thể kết nối đến Minecraft Server. "
                                + "Vui lòng liên hệ Admin để được hỗ trợ!"
                ).queue();
            } catch (Exception e) {
                logger.error("Lỗi hệ thống khi xử lý đăng ký taikhoan={}, ingame={}: {}",
                        taiKhoanNet, tenIngame, e.getMessage(), e);
                event.getHook().editOriginal(
                        "⚠️ Xác thực tài khoản thành công nhưng xảy ra lỗi hệ thống khi cấp quyền. "
                                + "Vui lòng liên hệ Admin để được hỗ trợ!"
                ).queue();
            }
        }).exceptionally(throwable -> {
            logger.error("Lỗi không mong đợi khi xử lý /dangky: {}", throwable.getMessage(), throwable);
            event.getHook().editOriginal(
                    "⚠️ Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ Admin!"
            ).queue();
            return null;
        });
    }

    /**
     * Tìm và gán Role SMP cho Member đã thực thi lệnh.
     * Đọc ROLE_SMP_ID từ biến môi trường.
     *
     * @throws IllegalStateException Nếu thiếu cấu hình hoặc không tìm thấy Role
     */
    private void assignDiscordRole(SlashCommandInteractionEvent event) {
        String roleSmpId = EnvLoader.get("ROLE_SMP_ID");
        if (roleSmpId == null || roleSmpId.isBlank()) {
            logger.warn("Biến môi trường ROLE_SMP_ID chưa được cấu hình!");
            throw new IllegalStateException("ROLE_SMP_ID chưa được cấu hình trong môi trường.");
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            throw new IllegalStateException("Lệnh chỉ có thể sử dụng trong server Discord.");
        }

        Role role = guild.getRoleById(roleSmpId);
        if (role == null) {
            logger.warn("Không tìm thấy Role với ID: {}", roleSmpId);
            throw new IllegalStateException("Không tìm thấy Role SMP với ID: " + roleSmpId);
        }

        Member member = event.getMember();
        if (member == null) {
            throw new IllegalStateException("Không xác định được thành viên thực hiện lệnh.");
        }

        // Gán role cho member (bất đồng bộ qua JDA)
        guild.addRoleToMember(member, role).queue(
                success -> logger.info("Đã cấp Role '{}' cho thành viên '{}'",
                        role.getName(), member.getUser().getName()),
                error -> logger.error("Không thể cấp Role '{}' cho '{}': {}",
                        role.getName(), member.getUser().getName(), error.getMessage())
        );
    }
}
