package dev.vaultauth.auth;

import dev.vaultauth.credentials.Credentials;
import dev.vaultauth.model.User;

/**
 * Core abstraction for an authentication step.
 * Each scheme takes credentials and returns the authenticated user,
 * or throws if the credentials are invalid.
 *
 * Keeping this as an interface means new schemes (hardware key, SSO, etc.)
 * can be plugged in without touching the filter or controller.
 */
public interface AuthenticationScheme {
    User authenticate(Credentials credentials) throws AuthenticationException;
    String getSchemeName();
}
