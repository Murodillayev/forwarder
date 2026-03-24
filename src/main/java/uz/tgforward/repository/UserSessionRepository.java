package uz.tgforward.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByOwner(AppUser owner);

    Optional<UserSession> findByOwner_TelegramId(Long telegramId);

    Optional<UserSession> findByOwner_Id(UUID ownerId);

    List<UserSession> findAllByStatus(UserSession.SessionStatus status);
}
