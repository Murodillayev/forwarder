package uz.tgforward.bot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import uz.tgforward.domain.ForwardConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BotConversationState — har bir Telegram foydalanuvchi uchun
 * joriy suhbat holatini saqlaydi.
 *
 * Step-by-step oqimlar:
 *   NEW_CONFIG  → SOURCE → TARGET → MARKUP → PATTERN → HEADER → FOOTER → DONE
 *   SESSION     → PHONE  → CODE   → (2FA)  → DONE
 */
@Component
public class BotConversationState {

    public enum Step {
        NONE,

        // Config yaratish
        CONFIG_SOURCE,
        CONFIG_TARGET,
        CONFIG_MARKUP,
        CONFIG_PATTERN,
        CONFIG_HEADER,
        CONFIG_FOOTER,

        // Session
        SESSION_PHONE,
        SESSION_CODE,
        SESSION_2FA
    }

    @Getter @Setter
    public static class UserState {
        private Step step = Step.NONE;

        // Config yaratish uchun to'plangan ma'lumotlar
        private String sourceChannel;
        private String targetChannel;
        private double markupPercent = 20.0;
        private ForwardConfig.PatternType patternType = ForwardConfig.PatternType.FORMAT_A;
        private String customPattern;
        private String headerText;
        private String footerText;

        // Tahrirlash uchun — qaysi config tahrirlanmoqda
        private UUID editingConfigId;

        public void reset() {
            step = Step.NONE;
            sourceChannel = null;
            targetChannel = null;
            markupPercent = 20.0;
            patternType = ForwardConfig.PatternType.FORMAT_A;
            customPattern = null;
            headerText = null;
            footerText = null;
            editingConfigId = null;
        }
    }

    // telegramId → state
    private final Map<Long, UserState> states = new ConcurrentHashMap<>();

    public UserState get(Long telegramId) {
        return states.computeIfAbsent(telegramId, k -> new UserState());
    }

    public void reset(Long telegramId) {
        states.computeIfAbsent(telegramId, k -> new UserState()).reset();
    }
}
