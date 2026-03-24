package uz.tgforward.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.ForwardConfig;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.service.ForwardConfigService;

import java.util.UUID;

@Controller
@RequestMapping("/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ForwardConfigService configService;

    // ── Ro'yxat ──────────────────────────────────
    @GetMapping
    public String list(HttpSession session, Model model) {
        AppUser user = currentUser(session);
        model.addAttribute("configs", configService.getUserConfigs(user));
        model.addAttribute("user", user);
        return "configs/list";
    }

    // ── Yaratish formasi ─────────────────────────
    @GetMapping("/new")
    public String newForm(Model model, HttpSession session) {
        model.addAttribute("config", new ForwardConfig());
        model.addAttribute("patternTypes", ForwardConfig.PatternType.values());
        model.addAttribute("user", currentUser(session));
        return "configs/form";
    }

    // ── Saqlash ──────────────────────────────────
    @PostMapping
    public String create(
            @ModelAttribute("config") ForwardConfig config,
            BindingResult result,
            HttpSession session,
            RedirectAttributes flash,
            Model model) {

        if (result.hasErrors()) {
            model.addAttribute("patternTypes", ForwardConfig.PatternType.values());
            model.addAttribute("user", currentUser(session));
            return "configs/form";
        }

        try {
            configService.create(currentUser(session), config);
            flash.addFlashAttribute("success", "Config muvaffaqiyatli yaratildi!");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/configs";
    }

    // ── Tahrirlash formasi ───────────────────────
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, HttpSession session, Model model) {
        AppUser user = currentUser(session);
        ForwardConfig config = configService.getOwned(user, id);
        model.addAttribute("config", config);
        model.addAttribute("patternTypes", ForwardConfig.PatternType.values());
        model.addAttribute("user", user);
        model.addAttribute("editMode", true);
        return "configs/form";
    }

    // ── Yangilash ────────────────────────────────
    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @ModelAttribute("config") ForwardConfig config,
            BindingResult result,
            HttpSession session,
            RedirectAttributes flash,
            Model model) {

        if (result.hasErrors()) {
            model.addAttribute("patternTypes", ForwardConfig.PatternType.values());
            model.addAttribute("user", currentUser(session));
            model.addAttribute("editMode", true);
            return "configs/form";
        }

        try {
            configService.update(currentUser(session), id, config);
            flash.addFlashAttribute("success", "Config yangilandi.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/configs";
    }

    // ── Aktiv/Inaktiv ────────────────────────────
    @PostMapping("/{id}/toggle")
    public String toggle(
            @PathVariable UUID id,
            @RequestParam boolean active,
            HttpSession session,
            RedirectAttributes flash) {
        try {
            configService.toggleActive(currentUser(session), id, active);
            flash.addFlashAttribute("success", active ? "Config yoqildi." : "Config to'xtatildi.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/configs";
    }

    // ── O'chirish ────────────────────────────────
    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID id,
            HttpSession session,
            RedirectAttributes flash) {
        try {
            configService.delete(currentUser(session), id);
            flash.addFlashAttribute("success", "Config o'chirildi.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/configs";
    }

    // ── Helper ───────────────────────────────────
    private AppUser currentUser(HttpSession session) {
        AppUser user = (AppUser) session.getAttribute("currentUser");
        if (user != null) return user;

        // Phone login sonrasida currentUser sessiyada bo'lmasligi mumkin
        // Spring Security authentication dan olamiz
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("Sessiya tugagan. Qayta kiring.");
        }

        String principal = auth.getName(); // phone yoki telegramId
        var found = principal.startsWith("+")
                ? configService.findUserByPhone(principal)
                : configService.findUserByTelegramId(Long.parseLong(principal));

        found.ifPresent(u -> session.setAttribute("currentUser", u));
        return found.orElseThrow(() -> new BusinessException("Sessiya tugagan. Qayta kiring."));
    }
}

// 1. HUB -> (DB save) -> save redis         |   agent (5 min)

//hub [change pass -> save db , save cache ("req_id": {type: CHANGE_PASS, status: PENDING, data: {"oldPass":"123","userId":12321}) ]              agent -> fail -> call rolback (req_id)
//hub [change pass -> save db , save cache ("req_id": {type: CHANGE_ROLE, status: PENDING, data: {"old_roles":[1,2,3],"userId":12321}) ]              agent -> fail -> call rolback (req_id)


// 1 , 2, 3     -> revoke 1 , 2  -> 1 , 2