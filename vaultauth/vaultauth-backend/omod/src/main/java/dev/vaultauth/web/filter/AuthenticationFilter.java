package dev.vaultauth.web.filter;

import dev.vaultauth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * The core of VaultAuth's SPA-compatible auth challenge flow.
 *
 * Classic Spring Security redirects the browser to a login page (302 → HTML).
 * That breaks React SPAs — a fetch() call getting back HTML is useless.
 *
 * This filter intercepts every /api/** request and checks session state:
 *   - Fully authenticated  → pass through
 *   - Partially authed     → 401 + Location pointing to the next step
 *   - Not authed at all    → 401 + Location pointing to /login
 *
 * The Location header tells the SPA exactly where to navigate next.
 * No HTML, no redirects, no JSP. Just machine-readable signals.
 *
 * This pattern is also how you'd solve this problem in server-rendered
 * SPAs like OpenMRS O3, where the backend was built around JSP challenge
 * URLs that the SPA cannot render.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final AuthService authService;

    @Value("${vaultauth.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // paths that are always public — no auth check needed
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/session",
            "/api/auth/totp",
            "/api/auth/challenge",
            "/api/auth/password",
            "/api/auth/totp/setup",
            "/api/auth/totp/confirm"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getServletPath();

        // always let public auth endpoints through
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);

        if (session != null && Boolean.TRUE.equals(session.getAttribute(AuthService.SESSION_KEY_AUTHENTICATED))) {
            // fully authenticated — pass through
            chain.doFilter(request, response);
            return;
        }

        // not authenticated — work out where the SPA should go next
        String challengeUrl = buildChallengeUrl(session);

        // inject Location header so the SPA knows where to redirect the user
        response.setHeader("Location", challengeUrl);
        response.setHeader("Content-Type", "application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\":\"Authentication required\",\"challengeUrl\":\"" + challengeUrl + "\"}");
    }

    /**
     * Build the challenge URL based on where the user is in the auth flow.
     * If they're mid-flow (e.g. TOTP pending), send them back to that step.
     * Otherwise send them to the start of login.
     */
    private String buildChallengeUrl(HttpSession session) {
        if (session == null) {
            return frontendUrl + "/login";
        }
        String step = (String) session.getAttribute(AuthService.SESSION_KEY_AUTH_STEP);
        if (step == null) {
            return frontendUrl + "/login";
        }
        return switch (step) {
            case AuthService.STEP_TOTP_PENDING     -> frontendUrl + "/login?step=totp";
            case AuthService.STEP_CHALLENGE_PENDING -> frontendUrl + "/login?step=challenge";
            case AuthService.STEP_PASSWORD_CHANGE   -> frontendUrl + "/login?step=change-password";
            default -> frontendUrl + "/login";
        };
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // only run this filter on /api/** requests
        return !request.getServletPath().startsWith("/api/");
    }
}
