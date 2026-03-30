package dev.vaultauth.web.rest;

import dev.vaultauth.auth.AuthenticationException;
import dev.vaultauth.model.User;
import dev.vaultauth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Step 1 — username + password.
     *
     * Returns:
     *   200 { step: "COMPLETE" }           → fully authenticated, no 2FA
     *   200 { step: "TOTP_PENDING" }       → redirect to TOTP step
     *   200 { step: "CHALLENGE_PENDING" }  → redirect to secret question step
     *   200 { step: "PASSWORD_CHANGE" }    → redirect to force-change-password
     *   401 { error: "..." }               → bad credentials or locked account
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> body,
            HttpSession session,
            HttpServletRequest request) {

        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username and password are required"));
        }

        try {
            String nextStep = authService.authenticatePrimary(username, password, session, request);
            return ResponseEntity.ok(Map.of("step", nextStep));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Step 2a — TOTP code submission.
     * Requires that step 1 was completed in the same session.
     */
    @PostMapping("/totp")
    public ResponseEntity<Map<String, String>> submitTotp(
            @RequestBody Map<String, String> body,
            HttpSession session,
            HttpServletRequest request) {

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
        }

        try {
            authService.authenticateTotp(code.trim(), session, request);
            return ResponseEntity.ok(Map.of("step", AuthService.STEP_COMPLETE));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Step 2b — secret question answer submission.
     */
    @PostMapping("/challenge")
    public ResponseEntity<Map<String, String>> submitChallenge(
            @RequestBody Map<String, String> body,
            HttpSession session,
            HttpServletRequest request) {

        String answer = body.get("answer");
        if (answer == null || answer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "answer is required"));
        }

        try {
            authService.authenticateChallenge(answer, session, request);
            return ResponseEntity.ok(Map.of("step", AuthService.STEP_COMPLETE));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Force-password-change step.
     * User is partially authenticated — locked until password is updated.
     */
    @PostMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody Map<String, String> body,
            HttpSession session,
            HttpServletRequest request) {

        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 8 characters"));
        }

        try {
            authService.changePassword(newPassword, session, request);
            return ResponseEntity.ok(Map.of("step", AuthService.STEP_COMPLETE));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Current session state — the SPA polls this on load to know where to go.
     * Returns 200 with user info if authenticated, 401 + Location if not.
     * The filter handles the 401 case — this endpoint only runs if fully authed.
     */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(HttpSession session) {
        Optional<User> user = authService.getAuthenticatedUser(session);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        User u = user.get();
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "displayName", u.getDisplayName(),
                "email", u.getEmail(),
                "role", u.getRole().name(),
                "twoFactorEnabled", u.isTwoFactorEnabled()
        ));
    }

    /**
     * Logout — invalidates session, writes audit log entry.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpSession session,
            HttpServletRequest request) {
        authService.logout(session, request);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
