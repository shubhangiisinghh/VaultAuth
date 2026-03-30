package dev.vaultauth.web.rest;

import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.vaultauth.auth.AuthenticationException;
import dev.vaultauth.model.SessionEvent;
import dev.vaultauth.model.User;
import dev.vaultauth.service.AuthService;
import dev.vaultauth.service.SessionAuditService;
import dev.vaultauth.service.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/totp")
@RequiredArgsConstructor
public class TotpSetupController {

    private final TotpService totpService;
    private final AuthService authService;
    private final SessionAuditService auditService;

    /**
     * Generate a new TOTP secret + QR code for the authenticated user.
     * Returns a data URI the frontend can drop straight into an <img> tag.
     */
    @GetMapping("/setup")
    public ResponseEntity<Map<String, String>> setup(HttpSession session) {
        Optional<User> user = authService.getAuthenticatedUser(session);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        try {
            String qrDataUri = totpService.generateSetup(user.get());
            return ResponseEntity.ok(Map.of("qrCode", qrDataUri));
        } catch (QrGenerationException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate QR code"));
        }
    }

    /**
     * Confirm TOTP enrolment — user submits the first code after scanning.
     * On success, 2FA is enabled for the user going forward.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(
            @RequestBody Map<String, String> body,
            HttpSession session,
            HttpServletRequest request) {

        Optional<User> user = authService.getAuthenticatedUser(session);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
        }

        boolean confirmed = totpService.confirmEnrolment(user.get(), code.trim());
        if (!confirmed) {
            return ResponseEntity.status(400)
                    .body(Map.of("error", "Invalid code — make sure your authenticator app is synced"));
        }

        auditService.log(user.get().getUsername(), SessionEvent.EventType.TOTP_ENROLLED, request, null);
        return ResponseEntity.ok(Map.of("message", "TOTP enrolled successfully"));
    }
}
