package uz.tgforward.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.tgforward.domain.AppUser;
import uz.tgforward.repository.AppUserRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Telegram Login Widget dan kelgan ma'lumotlarni tekshiradi.
 *
 * Telegram spesifikatsiyasi:
 * https://core.telegram.org/widgets/login#checking-authorization
 *
 * Tekshirish algoritmi:
 *   1. hash parametrini olib tashlaydi
 *   2. Qolgan parametrlarni "key=value" ko'rinishida saralab "\n" bilan birlashtiradi
 *   3. HMAC-SHA256(data_check_string, SHA256(bot_token)) hisoblaydi
 *   4. Natijani kelgan hash bilan solishtiradi
 *   5. auth_date 1 soatdan eski bo'lsa rad etadi
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramAuthService {

    @Value("${app.telegram-bot-token}")
    private String botToken;

    private final AppUserRepository userRepo;

    /**
     * Telegram Widget parametrlarini tekshirib, foydalanuvchini qaytaradi.
     * Yangi foydalanuvchi bo'lsa DB ga yozadi.
     */
    public AppUser verifyAndGetUser(Map<String, String> params) {
        if (!isValidHash(params)) {
            throw new SecurityException("Telegram auth hash noto'g'ri");
        }

        if (isExpired(params.get("auth_date"))) {
            throw new SecurityException("Telegram auth muddati o'tgan (1 soat)");
        }

        Long telegramId = Long.parseLong(params.get("id"));

        return userRepo.findByTelegramId(telegramId).orElseGet(() -> {
            AppUser newUser = AppUser.builder()
                .telegramId(telegramId)
                .username(params.get("username"))
                .firstName(params.get("first_name"))
                .lastName(params.get("last_name"))
                .photoUrl(params.get("photo_url"))
                .planType(AppUser.PlanType.FREE)
                .build();
            AppUser saved = userRepo.save(newUser);
            log.info("Yangi foydalanuvchi: @{} ({})", saved.getUsername(), telegramId);
            return saved;
        });
    }

    private boolean isValidHash(Map<String, String> params) {
        try {
            String receivedHash = params.get("hash");
            if (receivedHash == null) return false;

            // Parametrlarni saralab data_check_string yasaymiz
            TreeMap<String, String> sorted = new TreeMap<>(params);
            sorted.remove("hash");

            String dataCheckString = sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

            // secret_key = SHA256(bot_token)
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = sha256.digest(botToken.getBytes(StandardCharsets.UTF_8));

            // HMAC-SHA256(data_check_string, secret_key)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] hmac = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            String computedHash = bytesToHex(hmac);
            return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Hash tekshirishda xatolik: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(String authDate) {
        if (authDate == null) return true;
        long authTime = Long.parseLong(authDate);
        long now = System.currentTimeMillis() / 1000;
        return (now - authTime) > 3600; // 1 soat
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
