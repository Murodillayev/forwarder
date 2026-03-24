package uz.tgforward.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.tgforward.domain.AppUser;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.repository.AppUserRepository;
import uz.tgforward.service.ForwardConfigService;
import uz.tgforward.service.PhoneAuthService;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramBotHandler implements LongPollingSingleThreadUpdateConsumer {

    private final OkHttpTelegramClient telegramClient;
    private final AppUserRepository userRepo;
    private final ForwardConfigService configService;
    private final PhoneAuthService phoneAuthService;

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        var msg = update.getMessage();
        Long chatId = msg.getChatId();

        // ── Contact share (parol olish uchun) ────────────────
        if (msg.getContact() != null) {
            handleContact(chatId, msg);
            return;
        }

        if (!msg.hasText()) return;
        String text = msg.getText().trim();

        // Ro'yxatdan o'tkazish (agar yangi user bo'lsa)
        AppUser user = ensureRegistered(msg.getFrom());

        String reply = switch (text) {
            case "/start"       -> handleStart(user);
            case "/getpassword" -> handleGetPassword(chatId);
            case "/list"        -> handleList(user);
            default             -> "Noma'lum buyruq.\n/start — boshlash\n/getpassword — parol olish\n/list — configlar";
        };

        send(chatId, reply, null);
    }

    // ── /start ───────────────────────────────────────────────
    private String handleStart(AppUser user) {
        return """
            Salom, <b>%s</b>! 👋

            Bu bot diller kanallaridan postlarni avtomatik uzatadi.

            <b>Web panelga kirish usullari:</b>
            1. Telegram widget (shu akkount orqali)
            2. Telefon + parol → /getpassword

            <b>Buyruqlar:</b>
            /getpassword — telefon orqali parol olish
            /list — configlarni ko'rish
            """.formatted(user.getDisplayName());
    }

    // ── /getpassword — telefon so'rash ───────────────────────
    private String handleGetPassword(Long chatId) {
        // Telefon raqamini so'rash uchun maxsus keyboard
        KeyboardButton contactBtn = KeyboardButton.builder()
            .text("📱 Telefon raqamimni yuborish")
            .requestContact(true)
            .build();

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
            .keyboard(List.of(new KeyboardRow(List.of(contactBtn))))
            .resizeKeyboard(true)
            .oneTimeKeyboard(true)
            .build();

        send(chatId,
            "Parol olish uchun telefon raqamingizni yuboring 👇\n\n" +
            "<i>Faqat o'zingizning raqamingizni yuboring</i>",
            keyboard);

        return null; // send allaqachon chaqirildi
    }

    // ── Contact keldi — parol generatsiya ───────────────────
    private void handleContact(Long chatId, Message msg) {
        var contact = msg.getContact();

        // Faqat o'z raqamini yuborishiga ishonch hosil qilish
        if (!contact.getUserId().equals(chatId)) {
            send(chatId, "❌ Faqat o'z telefon raqamingizni yuboring.", removeKeyboard());
            return;
        }

        try {
            String code = phoneAuthService.generatePassword(chatId, contact.getPhoneNumber());

            String reply = """
                ✅ <b>Parolingiz tayyor!</b>

                📱 Telefon: <code>+%s</code>
                🔑 Parol: <code>%s</code>

                ⏱ <b>%d daqiqa</b> ichida ishlatish kerak.

                Web panelga kirish: telefon va parolni kiriting.
                """.formatted(
                    contact.getPhoneNumber().replaceFirst("^\\+", ""),
                    code,
                    10
                );

            send(chatId, reply, removeKeyboard());

        } catch (BusinessException e) {
            send(chatId, "❌ " + e.getMessage(), removeKeyboard());
        }
    }

    // ── /list ────────────────────────────────────────────────
    private String handleList(AppUser user) {
        var configs = configService.getUserConfigs(user);
        if (configs.isEmpty()) {
            return "Configlar yo'q. Web panel orqali yarating.";
        }
        var sb = new StringBuilder("<b>Configlar:</b>\n\n");
        configs.forEach(c -> sb
            .append(c.isActive() ? "🟢 " : "🔴 ")
            .append(c.getSourceChannel()).append(" → ").append(c.getTargetChannel())
            .append("  +").append(c.getMarkupPercent()).append("%\n"));
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────
    private AppUser ensureRegistered(org.telegram.telegrambots.meta.api.objects.User from) {
        return userRepo.findByTelegramId(from.getId()).orElseGet(() ->
            userRepo.save(AppUser.builder()
                .telegramId(from.getId())
                .username(from.getUserName())
                .firstName(from.getFirstName())
                .lastName(from.getLastName())
                .planType(AppUser.PlanType.FREE)
                .build())
        );
    }

    private void send(Long chatId, String text,
                      org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard keyboard) {
        if (text == null) return;
        try {
            var builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML");
            if (keyboard != null) builder.replyMarkup(keyboard);
            telegramClient.execute(builder.build());
        } catch (Exception e) {
            log.error("Xabar yuborib bo'lmadi [{}]: {}", chatId, e.getMessage());
        }
    }

    private ReplyKeyboardRemove removeKeyboard() {
        return ReplyKeyboardRemove.builder().removeKeyboard(true).build();
    }
}
