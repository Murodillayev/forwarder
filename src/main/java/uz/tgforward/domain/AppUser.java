package uz.tgforward.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────────────────────
// AppUser
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private Long telegramId;

    @Column(unique = true)
    private String username;       // Telegram @username (@ siz)

    private String firstName;
    private String lastName;
    private String photoUrl;       // Telegram avatar URL

    // ── Telefon orqali login ──────────────────
    @Column(unique = true)
    private String phoneNumber;       // +998901234567 formatida

    private String passwordHash;      // BCrypt(5 xonali kod)

    private LocalDateTime passwordExpiresAt;  // Generatsiya vaqti + 10 daqiqa

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PlanType planType = PlanType.FREE;

    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ForwardConfig> configs;

    public enum PlanType { FREE, PRO, ADMIN }

    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) return firstName;
        if (username != null) return "@" + username;
        return "User#" + telegramId;
    }
}
