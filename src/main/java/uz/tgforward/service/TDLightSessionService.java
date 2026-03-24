package uz.tgforward.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.UserSession;
import uz.tgforward.tdlight.TDLightSessionManager;

import java.util.List;

/**
 * TDLightSessionService — Controller va TDLightSessionManager orasidagi ko'prik.
 * Validatsiya va exception handling shu yerda.
 */
@Service
@RequiredArgsConstructor
public class TDLightSessionService {

    private final TDLightSessionManager manager;

    public void startAuth(AppUser owner, String phone) {
        String normalized = normalizePhone(phone);
        manager.startAuth(owner, normalized);
    }

    public void submitCode(AppUser owner, String code) {
        if (code == null || code.isBlank()) throw new uz.tgforward.exception.BusinessException("Kod bo'sh bo'lmasin");
        manager.submitCode(owner, code.trim());
    }

    public void submit2fa(AppUser owner, String password) {
        if (password == null || password.isBlank()) throw new uz.tgforward.exception.BusinessException("Parol bo'sh bo'lmasin");
        manager.submit2fa(owner, password);
    }

    public void destroySession(AppUser owner) {
        manager.destroySession(owner);
    }

    public UserSession getSessionOrNull(AppUser owner) {
        try { return manager.getSession(owner); }
        catch (Exception e) { return null; }
    }

    public boolean isActive(AppUser owner) {
        return manager.isActive(owner);
    }

    public List<TDLightSessionManager.ChatInfo> getUserChats(AppUser owner) {
        return manager.fetchUserChats(owner);
    }

    private String normalizePhone(String phone) {
        if (phone == null) throw new uz.tgforward.exception.BusinessException("Telefon raqam bo'sh");
        phone = phone.trim().replaceAll("\\s+", "");
        return phone.startsWith("+") ? phone : "+" + phone;
    }
}
