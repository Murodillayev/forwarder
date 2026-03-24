package uz.tgforward.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserSession — har bir foydalanuvchining TDLight (MTProto) session holati.
 *
 * Holat diagrammasi:
 *
 *   NONE → [telefon kiritildi] → PENDING_CODE
 *        → [SMS kod kiritildi] → PENDING_2FA (agar 2FA yoqilgan bo'lsa)
 *        → [parol kiritildi]   → ACTIVE
 *        → [xato / logout]     → INACTIVE
 *
 * Session fayllari ${tdlight.sessions-dir}/{userId}/ papkasida saqlanadi.
 * Bu papka juda sezgir — faqat tgforward user o'qiy olishi kerak (chmod 700).
 */
@Entity
@Table(name = "user_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private AppUser owner;

    @Column(nullable = false)
    private String phoneNumber;       // +998901234567

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.NONE;

    // TDLight auth jarayonida keladigan phoneCodeHash — kodni tekshirish uchun kerak
    private String phoneCodeHash;

    // Xato xabari (INACTIVE holatda)
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum SessionStatus {
        NONE,            // Hali boshlangan emas
        PENDING_CODE,    // SMS kod kutilmoqda
        PENDING_2FA,     // 2FA paroli kutilmoqda
        ACTIVE,          // Session ishlayapti
        INACTIVE         // Xato yoki logout
    }
}
