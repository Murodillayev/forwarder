package uz.tgforward.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tgforward.domain.ProcessedPost;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProcessedPostRepository extends JpaRepository<ProcessedPost, UUID> {
    boolean existsByTelegramMessageIdAndSourceChannel(Long messageId, String sourceChannel);
    void deleteByProcessedAtBefore(LocalDateTime before);
}

