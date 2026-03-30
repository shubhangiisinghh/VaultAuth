package dev.vaultauth.web.rest;

import dev.vaultauth.model.SessionEvent;
import dev.vaultauth.model.User;
import dev.vaultauth.model.UserRole;
import dev.vaultauth.service.AuthService;
import dev.vaultauth.service.SessionAuditService;
import dev.vaultauth.service.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SessionAuditService auditService;
    private final UserRepository userRepository;
    private final AuthService authService;

    /**
     * Paginated session event log — admins only.
     */
    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            HttpSession session) {

        if (!isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Page<SessionEvent> events = username != null && !username.isBlank()
                ? auditService.getEventsByUser(username, PageRequest.of(page, size))
                : auditService.getAllEvents(PageRequest.of(page, size));

        return ResponseEntity.ok(Map.of(
                "events", events.getContent(),
                "totalElements", events.getTotalElements(),
                "totalPages", events.getTotalPages(),
                "page", events.getNumber()
        ));
    }

    /**
     * List all users — admins only.
     */
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(userRepository.findAll().stream().map(u -> Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "displayName", u.getDisplayName(),
                "email", u.getEmail(),
                "role", u.getRole().name(),
                "twoFactorEnabled", u.isTwoFactorEnabled(),
                "accountLocked", u.isAccountLocked(),
                "forcePasswordChange", u.isForcePasswordChange()
        )).toList());
    }

    /**
     * Unlock a user account — admins only.
     */
    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return userRepository.findById(id).map(u -> {
            u.setAccountLocked(false);
            u.setFailedLoginAttempts(0);
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("message", "Account unlocked"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Force a password reset on next login — admins only.
     */
    @PostMapping("/users/{id}/force-password-change")
    public ResponseEntity<?> forcePasswordChange(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return userRepository.findById(id).map(u -> {
            u.setForcePasswordChange(true);
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("message", "Password reset required on next login"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAdmin(HttpSession session) {
        Optional<User> user = authService.getAuthenticatedUser(session);
        return user.map(u -> u.getRole() == UserRole.ADMIN).orElse(false);
    }
}
