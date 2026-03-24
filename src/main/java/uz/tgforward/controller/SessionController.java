package uz.tgforward.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.UserSession;
import uz.tgforward.domain.UserSession.SessionStatus;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.service.TDLightSessionService;

import java.util.List;

/**
 * SessionController — foydalanuvchi TDLight sessionini boshqaradi.
 *
 * URL:
 *   GET  /session          — holat sahifasi
 *   POST /session/start    — telefon kiritib auth boshlash
 *   POST /session/code     — SMS kod kiritish
 *   POST /session/2fa      — 2FA paroli kiritish
 *   POST /session/destroy  — sessionni o'chirish
 */
@Controller
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final TDLightSessionService sessionService;

    // ── Holat sahifasi ────────────────────────────────────────
    @GetMapping
    public String status(HttpSession httpSession, Model model) {
        AppUser user = currentUser(httpSession);
        UserSession session = sessionService.getSessionOrNull(user);
        model.addAttribute("user", user);
        model.addAttribute("session", session);
        model.addAttribute("status", session != null ? session.getStatus() : SessionStatus.NONE);
        return "session/status";
    }

    // ── Telefon kiritish → auth boshlash ──────────────────────
    @PostMapping("/start")
    public String start(
            @RequestParam String phone,
            HttpSession httpSession,
            RedirectAttributes flash) {
        try {
            sessionService.startAuth(currentUser(httpSession), phone);
            flash.addFlashAttribute("info", "SMS kod yuborildi. Kodni kiriting.");
        } catch (Exception e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/session";
    }

    // ── SMS kodni kiritish ────────────────────────────────────
    @PostMapping("/code")
    public String submitCode(
            @RequestParam String code,
            HttpSession httpSession,
            RedirectAttributes flash) {
        try {
            sessionService.submitCode(currentUser(httpSession), code);
            flash.addFlashAttribute("info", "Kod qabul qilindi. Tekshirilmoqda...");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/session";
    }

    // ── 2FA paroli ────────────────────────────────────────────
    @PostMapping("/2fa")
    public String submit2fa(
            @RequestParam String password,
            HttpSession httpSession,
            RedirectAttributes flash) {
        try {
            sessionService.submit2fa(currentUser(httpSession), password);
            flash.addFlashAttribute("info", "Parol qabul qilindi. Tekshirilmoqda...");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/session";
    }

    // ── Kanallar ro'yxati ─────────────────────────────────────
    @GetMapping("/chats")
    public String chats(HttpSession httpSession, Model model) {
        AppUser user = currentUser(httpSession);
        try {
            var chats = sessionService.getUserChats(user);
            model.addAttribute("chats", chats);
        } catch (uz.tgforward.exception.BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chats", List.of());
        }
        model.addAttribute("user", user);
        model.addAttribute("session", sessionService.getSessionOrNull(user));
        model.addAttribute("status", sessionService.getSessionOrNull(user) != null
                ? sessionService.getSessionOrNull(user).getStatus()
                : uz.tgforward.domain.UserSession.SessionStatus.NONE);
        return "session/status";
    }

    // ── Sessionni o'chirish ───────────────────────────────────
    @PostMapping("/destroy")
    public String destroy(HttpSession httpSession, RedirectAttributes flash) {
        try {
            sessionService.destroySession(currentUser(httpSession));
            flash.addFlashAttribute("success", "Session o'chirildi.");
        } catch (Exception e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/session";
    }

    private AppUser currentUser(HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("currentUser");
        if (user == null) throw new BusinessException("Sessiya tugagan. Qayta kiring.");
        return user;
    }
}
