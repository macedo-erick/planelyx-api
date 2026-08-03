package br.com.fintrackapi.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated owner id from the {@code sub} claim of the
 * validated Keycloak JWT bound to the current request.
 */
@Component
public class CurrentUser {

    public UUID ownerId() {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken jwtAuthentication) {
            return UUID.fromString(jwtAuthentication.getToken().getSubject());
        }
        throw new IllegalStateException("No authenticated JWT principal in the security context");
    }
}
