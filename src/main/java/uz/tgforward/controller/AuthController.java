package uz.tgforward.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.tgforward.domain.AppUser;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.repository.AppUserRepository;
import uz.tgforward.service.PhoneAuthService;
import uz.tgforward.service.TelegramAuthService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    private final PhoneAuthService phoneAuthService;
    private final AppUserRepository userRepo;

    // ── Login sahifasi ────────────────────────────────────────
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String phoneError,
            Model model) {
        if (logout    != null) model.addAttribute("message", "Tizimdan chiqildi.");
        if (error     != null) model.addAttribute("error", "Kirish xatosi. Qayta urinib ko'ring.");
        if (phoneError!= null) model.addAttribute("phoneError", "Telefon yoki parol noto'g'ri.");
        return "auth/login";
    }

    // ── Telegram Widget callback ──────────────────────────────
    @GetMapping("/callback")
    public String telegramCallback(
            @RequestParam Map<String, String> params,
            HttpServletRequest request,
            RedirectAttributes flash) {
        try {
            AppUser user = telegramAuthService.verifyAndGetUser(params);
            openSession(user, user.getTelegramId().toString(), request);
            return "redirect:/configs";
        } catch (SecurityException e) {
            log.warn("Telegram auth rad etildi: {}", e.getMessage());
            flash.addFlashAttribute("error", "Telegram autentifikatsiya xatosi.");
            return "redirect:/auth/login";
        }
    }

    // ── Phone login — Spring Security /auth/phone-login ga POST qiladi,
    //    muvaffaqiyatli bo'lsa /configs ga yo'naltiradi.
    //    Bu yerda faqat expiry tekshiruvi kerak (muddati o'tganmi).
    //    Spring Security o'zi hash ni tekshiradi.
    //
    //    Expiry tekshiruvi uchun AuthenticationSuccessHandler ishlatamiz:
    //    login muvaffaqiyatli bo'lsa, sessiyani ochamiz va currentUser ni qo'shamiz.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/phone-success")
    public String phoneLoginSuccess(HttpServletRequest request, RedirectAttributes flash) {
        // Spring Security sessiya allaqachon ochiq.
        // Bizga currentUser ni sessiyaga yozish kerak.
        String username = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        var userOpt = username.startsWith("+")
            ? userRepo.findByPhoneNumber(username)
            : userRepo.findByTelegramId(Long.parseLong(username));

        userOpt.ifPresent(user -> {
            // Parol muddatini tekshirish
            if (user.getPasswordExpiresAt() != null
                    && LocalDateTime.now().isAfter(user.getPasswordExpiresAt())) {
                SecurityContextHolder.clearContext();
                flash.addFlashAttribute("phoneError", "Parol muddati o'tgan. Botda yangi parol oling.");
                return;
            }
            request.getSession().setAttribute("currentUser", user);
            // Bir martalik parolni o'chirish
            user.setPasswordHash(null);
            user.setPasswordExpiresAt(null);
            userRepo.save(user);
        });

        return "redirect:/configs";
    }

    // ── Helper: sessiya ochish (Telegram widget uchun) ────────
    private void openSession(AppUser user, String principalName, HttpServletRequest request) {
        var auth = new UsernamePasswordAuthenticationToken(
            principalName, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getPlanType().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext()
        );
        session.setAttribute("currentUser", user);
    }
}
