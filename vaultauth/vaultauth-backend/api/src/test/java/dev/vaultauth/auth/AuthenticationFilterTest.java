package dev.vaultauth.web.filter;

import dev.vaultauth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "frontendUrl", "http://localhost:5173");
    }

    /**
     * A fully authenticated session should pass straight through.
     * The filter must not set 401 or a Location header.
     */
    @Test
    void fullyAuthenticatedRequestPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_KEY_AUTHENTICATED, true);
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    /**
     * A request with no session should get 401 + Location pointing to /login.
     * This is the core SPA-compatible auth challenge behaviour.
     */
    @Test
    void unauthenticatedRequestGets401WithLocationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        // no session attached

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:5173/login");
        verify(chain, never()).doFilter(any(), any());
    }

    /**
     * A partial auth session (TOTP pending) should get 401 + Location pointing
     * to the TOTP step, not the start of login.
     */
    @Test
    void totpPendingSessionPointsToTotpStep() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_KEY_USER_ID, 1L);
        session.setAttribute(AuthService.SESSION_KEY_AUTH_STEP, AuthService.STEP_TOTP_PENDING);
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:5173/login?step=totp");
        verify(chain, never()).doFilter(any(), any());
    }

    /**
     * Public auth endpoints (login, logout, etc.) must never be blocked by the filter,
     * regardless of session state.
     */
    @Test
    void publicAuthEndpointsAreNeverBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        // deliberately no session

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    /**
     * Filter should not run at all for non-API paths.
     * shouldNotFilter() returning true means doFilterInternal is never called.
     */
    @Test
    void nonApiPathsAreSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
