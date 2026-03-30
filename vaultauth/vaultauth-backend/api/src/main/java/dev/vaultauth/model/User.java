package dev.vaultauth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "va_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // which second factor this user has enrolled: TOTP or QUESTION
    @Enumerated(EnumType.STRING)
    @Column
    private SecondFactorType secondFactorType;

    // whether 2FA is required for this user
    @Column(nullable = false)
    private boolean twoFactorEnabled;

    // for secret question 2FA
    @Column
    private String secretQuestion;

    @Column
    private String secretAnswerHash;

    // force the user to change password on next login
    @Column(nullable = false)
    private boolean forcePasswordChange;

    @Column(nullable = false)
    private boolean accountLocked;

    @Column(nullable = false)
    private int failedLoginAttempts;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
