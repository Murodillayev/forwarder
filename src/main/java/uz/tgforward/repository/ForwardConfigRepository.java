package uz.tgforward.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.ForwardConfig;

import java.util.List;
import java.util.UUID;

@Repository
public interface ForwardConfigRepository extends JpaRepository<ForwardConfig, UUID> {
    List<ForwardConfig> findAllByActiveTrue();

    List<ForwardConfig> findAllByOwnerOrderByCreatedAtDesc(AppUser owner);

    long countByOwner(AppUser owner);

    @Modifying
    @Query("UPDATE ForwardConfig f SET f.lastProcessedMessageId = :msgId WHERE f.id = :id")
    void updateLastMessageId(UUID id, Long msgId);
}
