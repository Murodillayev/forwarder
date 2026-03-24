package uz.tgforward.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.ForwardConfig;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.repository.AppUserRepository;
import uz.tgforward.repository.ForwardConfigRepository;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForwardConfigService {

    @Value("${app.free-plan-max-configs:2}")
    private int freePlanMax;

    private final ForwardConfigRepository configRepo;
    private final AppUserRepository userRepo;

    public List<ForwardConfig> getUserConfigs(AppUser user) {
        return configRepo.findAllByOwnerOrderByCreatedAtDesc(user);
    }

    @Transactional
    public ForwardConfig create(AppUser owner, ForwardConfig config) {
        if (owner.getPlanType() == AppUser.PlanType.FREE) {
            long count = configRepo.countByOwner(owner);
            if (count >= freePlanMax) {
                throw new BusinessException(
                    "Free planda maksimal " + freePlanMax + " ta config bo'lishi mumkin.");
            }
        }

        if (config.getPatternType() == ForwardConfig.PatternType.CUSTOM
                && (config.getCustomPattern() == null || config.getCustomPattern().isBlank())) {
            throw new BusinessException("CUSTOM uchun regex pattern majburiy.");
        }

        config.setOwner(owner);
        config.setSourceChannel(normalize(config.getSourceChannel()));
        config.setTargetChannel(normalize(config.getTargetChannel()));
        config.setActive(false);

        ForwardConfig saved = configRepo.save(config);
        log.info("Config yaratildi: {} → {} (user={})",
            saved.getSourceChannel(), saved.getTargetChannel(), owner.getTelegramId());
        return saved;
    }

    @Transactional
    public ForwardConfig update(AppUser owner, UUID id, ForwardConfig updated) {
        ForwardConfig existing = getOwned(owner, id);
        existing.setSourceChannel(normalize(updated.getSourceChannel()));
        existing.setTargetChannel(normalize(updated.getTargetChannel()));
        existing.setMarkupPercent(updated.getMarkupPercent());
        existing.setPatternType(updated.getPatternType());
        existing.setCustomPattern(updated.getCustomPattern());
        existing.setHeaderText(updated.getHeaderText());
        existing.setFooterText(updated.getFooterText());
        return configRepo.save(existing);
    }

    @Transactional
    public void toggleActive(AppUser owner, UUID id, boolean active) {
        ForwardConfig config = getOwned(owner, id);
        config.setActive(active);
        configRepo.save(config);
    }

    @Transactional
    public void delete(AppUser owner, UUID id) {
        ForwardConfig config = getOwned(owner, id);
        configRepo.delete(config);
    }

    public ForwardConfig getOwned(AppUser owner, UUID id) {
        ForwardConfig config = configRepo.findById(id)
            .orElseThrow(() -> new BusinessException("Config topilmadi."));
        if (!config.getOwner().getId().equals(owner.getId())) {
            throw new BusinessException("Bu config sizga tegishli emas.");
        }
        return config;
    }

    private String normalize(String ch) {
        if (ch == null) return null;
        ch = ch.trim();
        return (!ch.startsWith("@") && !ch.startsWith("-100")) ? "@" + ch : ch;
    }

    public java.util.Optional<AppUser> findUserByPhone(String phone) {
        return userRepo.findByPhoneNumber(phone);
    }

    public java.util.Optional<AppUser> findUserByTelegramId(Long telegramId) {
        return userRepo.findByTelegramId(telegramId);
    }
}
