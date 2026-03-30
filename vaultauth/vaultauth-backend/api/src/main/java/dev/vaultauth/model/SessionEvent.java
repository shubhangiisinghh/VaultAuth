package dev.vaultauth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "va_session_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    // extra detail e.g. "TOTP_INVALID_CODE", "ACCOUNT_LOCKED"
    @Column
    private String detail;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        occurredAt = LocalDateTime.now();
    }

    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        TOTP_SUCCESS,
        TOTP_FAILURE,
        CHALLENGE_SUCCESS,
        CHALLENGE_FAILURE,
        PASSWORD_CHANGED,
        LOGOUT,
        SESSION_TIMEOUT,
        ACCOUNT_LOCKED,
        TOTP_ENROLLED
    }
}
