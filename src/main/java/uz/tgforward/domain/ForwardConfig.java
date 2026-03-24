package uz.tgforward.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// ─────────────────────────────────────────────────────────
// ForwardConfig
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "forward_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ForwardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String sourceChannel;    // @diller_kanal

    @Column(nullable = false)
    private String targetChannel;    // @mening_kanal

    @Column(nullable = false)
    @Builder.Default
    private double markupPercent = 20.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PatternType patternType = PatternType.FORMAT_A;

    // CUSTOM pattern uchun: (?<model>...) va (?<price>...) named grouplar majburiy
    private String customPattern;

    private String headerText;
    private String footerText;

    @Builder.Default
    private boolean active = false;

    @Builder.Default
    private Long lastProcessedMessageId = 0L;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PatternType {
        FORMAT_A,   // "H119 1*4 26"        MODEL  K*D  NARX
        FORMAT_B,   // "Kidilo D9 - 1*1*61"  MODEL - K*D*NARX
        CUSTOM
    }
}

