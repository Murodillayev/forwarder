package uz.tgforward.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// ─────────────────────────────────────────────────────────
// ProcessedPost — duplicate oldini olish
// ─────────────────────────────────────────────────────────
@Entity
@Table(
    name = "processed_posts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"telegram_message_id", "source_channel"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "telegram_message_id", nullable = false)
    private Long telegramMessageId;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private ForwardConfig config;

    @Builder.Default
    private boolean parsed = true;

    @CreationTimestamp
    private LocalDateTime processedAt;
}
