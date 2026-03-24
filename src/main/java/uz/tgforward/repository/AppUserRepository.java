package uz.tgforward.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tgforward.domain.AppUser;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    Optional<AppUser> findByPhoneNumber(String phoneNumber);
}
