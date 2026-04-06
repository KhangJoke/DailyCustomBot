package unicorn.bot.dailycustombot.listener;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener xử lý Select Menu interaction cho ticket system.
 * Khi user chọn loại ticket, hiện Modal để nhập lý do.
 */
public class TicketSelectMenuListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketSelectMenuListener.class);

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("ticket_type_select")) {
            return; // Không phải select menu của ticket system
        }

        String selectedTypeId = event.getValues().get(0);

        logger.info("User {} selected ticket type: {}", event.getUser().getName(), selectedTypeId);

        // Tạo Modal để user nhập lý do
        TextInput reasonInput = TextInput.create("ticket_reason", "Lý do / Mô tả chi tiết", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Mô tả vấn đề của bạn ở đây...")
                .setMinLength(10)
                .setMaxLength(1000)
                .setRequired(true)
                .build();

        // Modal ID encode loại ticket: "ticket_modal:<typeId>"
        Modal modal = Modal.create("ticket_modal:" + selectedTypeId, "📝 Mô tả vấn đề")
                .addActionRow(reasonInput)
                .build();

        event.replyModal(modal).queue();
    }
}
