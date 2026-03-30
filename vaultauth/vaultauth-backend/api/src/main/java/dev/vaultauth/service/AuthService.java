package dev.vaultauth.service;

import dev.vaultauth.auth.AuthenticationException;
import dev.vaultauth.model.SecondFactorType;
import dev.vaultauth.model.SessionEvent;
import dev.vaultauth.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    // session keys
    public static final String SESSION_KEY_USER_ID       = "VA_USER_ID";
    public static final String SESSION_KEY_AUTH_STEP     = "VA_AUTH_STEP";
    public static final String SESSION_KEY_AUTHENTICATED = "VA_AUTHENTICATED";

    public static final String STEP_TOTP_PENDING     = "TOTP_PENDING";
    public static final String STEP_CHALLENGE_PENDING = "CHALLENGE_PENDING";
    public static final String STEP_PASSWORD_CHANGE   = "PASSWORD_CHANGE";
    public static final String STEP_COMPLETE          = "COMPLETE";

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final SessionAuditService auditService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Step 1 — validate username + password.
     * Returns the next auth step that the frontend should route to.
     * Does NOT mark the session as fully authenticated yet.
     */
    @Transactional
    public String authenticatePrimary(String username, String password,
                                      HttpSession session, HttpServletRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    auditService.log(username, SessionEvent.EventType.LOGIN_FAILURE, request, "user not found");
                    return new AuthenticationException("Invalid credentials");
                });

        if (user.isAccountLocked()) {
            auditService.log(username, SessionEvent.EventType.LOGIN_FAILURE, request, "account locked");
            throw new AuthenticationException("Account is locked. Please contact your administrator.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setAccountLocked(true);
                userRepository.save(user);
                auditService.log(username, SessionEvent.EventType.ACCOUNT_LOCKED, request, "too many failed attempts");
                throw new AuthenticationException("Account locked after too many failed attempts.");
            }
            userRepository.save(user);
            auditService.log(username, SessionEvent.EventType.LOGIN_FAILURE, request, "wrong password");
            throw new AuthenticationException("Invalid credentials");
        }

        // reset failed attempts on success
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        // store user id in session so step 2 can find them
        session.setAttribute(SESSION_KEY_USER_ID, user.getId());

        if (user.isForcePasswordChange()) {
            session.setAttribute(SESSION_KEY_AUTH_STEP, STEP_PASSWORD_CHANGE);
            return STEP_PASSWORD_CHANGE;
        }

        if (user.isTwoFactorEnabled()) {
            String step = user.getSecondFactorType() == SecondFactorType.TOTP
                    ? STEP_TOTP_PENDING : STEP_CHALLENGE_PENDING;
            session.setAttribute(SESSION_KEY_AUTH_STEP, step);
            return step;
        }

        // no 2FA — fully authenticated
        completeSession(session, user, request);
        return STEP_COMPLETE;
    }

    /**
     * Step 2a — validate TOTP code.
     */
    @Transactional
    public void authenticateTotp(String code, HttpSession session, HttpServletRequest request) {
        User user = getUserFromSession(session);
        requireStep(session, STEP_TOTP_PENDING);

        if (!totpService.validate(user, code)) {
            auditService.log(user.getUsername(), SessionEvent.EventType.TOTP_FAILURE, request, "invalid code");
            throw new AuthenticationException("Invalid or expired TOTP code.");
        }

        auditService.log(user.getUsername(), SessionEvent.EventType.TOTP_SUCCESS, request, null);
        completeSession(session, user, request);
    }

    /**
     * Step 2b — validate secret question answer.
     */
    @Transactional
    public void authenticateChallenge(String answer, HttpSession session, HttpServletRequest request) {
        User user = getUserFromSession(session);
        requireStep(session, STEP_CHALLENGE_PENDING);

        if (!passwordEncoder.matches(answer.toLowerCase().trim(), user.getSecretAnswerHash())) {
            auditService.log(user.getUsername(), SessionEvent.EventType.CHALLENGE_FAILURE, request, "wrong answer");
            throw new AuthenticationException("Incorrect answer.");
        }

        auditService.log(user.getUsername(), SessionEvent.EventType.CHALLENGE_SUCCESS, request, null);
        completeSession(session, user, request);
    }

    /**
     * Force password change flow.
     */
    @Transactional
    public void changePassword(String newPassword, HttpSession session, HttpServletRequest request) {
        User user = getUserFromSession(session);
        requireStep(session, STEP_PASSWORD_CHANGE);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);

        auditService.log(user.getUsername(), SessionEvent.EventType.PASSWORD_CHANGED, request, null);
        completeSession(session, user, request);
    }

    public void logout(HttpSession session, HttpServletRequest request) {
        Long userId = (Long) session.getAttribute(SESSION_KEY_USER_ID);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(u ->
                    auditService.log(u.getUsername(), SessionEvent.EventType.LOGOUT, request, null));
        }
        session.invalidate();
    }

    public Optional<User> getAuthenticatedUser(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute(SESSION_KEY_AUTHENTICATED))) {
            return Optional.empty();
        }
        Long userId = (Long) session.getAttribute(SESSION_KEY_USER_ID);
        if (userId == null) return Optional.empty();
        return userRepository.findById(userId);
    }

    private void completeSession(HttpSession session, User user, HttpServletRequest request) {
        session.setAttribute(SESSION_KEY_AUTHENTICATED, true);
        session.setAttribute(SESSION_KEY_AUTH_STEP, STEP_COMPLETE);
        auditService.log(user.getUsername(), SessionEvent.EventType.LOGIN_SUCCESS, request, null);
    }

    private User getUserFromSession(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_KEY_USER_ID);
        if (userId == null) throw new AuthenticationException("No active auth session. Please start from login.");
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Session user not found."));
    }

    private void requireStep(HttpSession session, String expected) {
        String step = (String) session.getAttribute(SESSION_KEY_AUTH_STEP);
        if (!expected.equals(step)) {
            throw new AuthenticationException("Unexpected auth step. Expected: " + expected + ", got: " + step);
        }
    }
}
