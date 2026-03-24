package uz.tgforward.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tgforward.domain.AppUser;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.repository.AppUserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * PhoneAuthService — telefon + 5 xonali kod orqali login.
 *
 * Oqim:
 *   1. Foydalanuvchi botda "Parol olish" tugmasini bosadi
 *   2. Telefon raqamini share qiladi
 *   3. Bot generatePassword() ni chaqiradi → 5 xonali kod → foydalanuvchiga yuboradi
 *   4. Foydalanuvchi web sahifada telefon + kodni kiritadi
 *   5. verifyAndGetUser() tekshiradi → AppUser qaytaradi
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PhoneAuthService {

    private static final int CODE_LENGTH_MIN = 10000; // 10000–99999 oralig'i
    private static final int CODE_RANGE      = 90000;
    private static final int EXPIRE_MINUTES  = 10;

    private final AppUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Botdan chaqiriladi.
     * Telefon raqami bo'yicha foydalanuvchini topadi (yoki yangi yaratadi),
     * 5 xonali kod generatsiya qilib hashlab saqlaydi.
     *
     * @param telegramId  Telegram user ID
     * @param phone       +998XXXXXXXXX formatida (Telegram contact dan keladi)
     * @return ochiq ko'rinishdagi 5 xonali kod (foydalanuvchiga yuborish uchun)
     */
    @Transactional
    public String generatePassword(Long telegramId, String phone) {
        String normalizedPhone = normalizePhone(phone);

        AppUser user = userRepo.findByTelegramId(telegramId)
            .orElseThrow(() -> new BusinessException("Avval /start bosing"));

        // Telefon raqamni saqlash (agar birinchi marta bo'lsa)
        if (user.getPhoneNumber() == null) {
            // Boshqa user da bu telefon bor-yo'qligini tekshirish
            userRepo.findByPhoneNumber(normalizedPhone).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new BusinessException("Bu telefon raqam boshqa akkountga bog'liq");
                }
            });
            user.setPhoneNumber(normalizedPhone);
        }

        // 5 xonali kod generatsiya
        int code = CODE_LENGTH_MIN + secureRandom.nextInt(CODE_RANGE);
        String codeStr = String.valueOf(code);

        // BCrypt hash saqlash
        user.setPasswordHash(passwordEncoder.encode(codeStr));
        user.setPasswordExpiresAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
        userRepo.save(user);

        log.info("Parol generatsiya qilindi: phone={}, telegramId={}", normalizedPhone, telegramId);
        return codeStr; // Botga qaytadi, foydalanuvchiga yuboriladi
    }

    /**
     * Web login formidan chaqiriladi.
     * Telefon + kod to'g'ri va muddati o'tmagan bo'lsa foydalanuvchini qaytaradi.
     */
    @Transactional
    public AppUser verifyAndGetUser(String phone, String code) {
        String normalizedPhone = normalizePhone(phone);

        AppUser user = userRepo.findByPhoneNumber(normalizedPhone)
            .orElseThrow(() -> new BusinessException("Bu telefon raqam ro'yxatdan o'tmagan. Botda /start bosing"));

        if (user.getPasswordHash() == null) {
            throw new BusinessException("Parol so'ralmagan. Botda 'Parol olish' tugmasini bosing");
        }

        if (user.getPasswordExpiresAt() == null
                || LocalDateTime.now().isAfter(user.getPasswordExpiresAt())) {
            throw new BusinessException("Parol muddati o'tgan (" + EXPIRE_MINUTES + " daqiqa). Botda yangi parol oling");
        }

        if (!passwordEncoder.matches(code.trim(), user.getPasswordHash())) {
            throw new BusinessException("Noto'g'ri parol");
        }

        // Muvaffaqiyatli login — parolni bir martalik qilish uchun o'chiramiz
        user.setPasswordHash(null);
        user.setPasswordExpiresAt(null);
        userRepo.save(user);

        log.info("Telefon orqali login: phone={}", normalizedPhone);
        return user;
    }

    /**
     * +998901234567 yoki 998901234567 → +998901234567
     */
    public String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim().replaceAll("\\s+", "");
        if (!phone.startsWith("+")) {
            phone = "+" + phone;
        }
        return phone;
    }
}
