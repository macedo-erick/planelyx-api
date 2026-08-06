package com.planelyx.api.security;

import com.planelyx.api.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Provisions the authenticated user before the request reaches a controller.
 *
 * Sits here rather than in a service so that setup is tied to the application seeing the user at
 * all, not to whichever endpoint they happen to open first.
 */
@Component
@RequiredArgsConstructor
public class UserProvisioningFilter extends OncePerRequestFilter {

    /**
     * Users this instance has already provisioned. Only an optimisation — {@code provisioned_owner}
     * is the real guard — so it never needs to be shared between instances or survive a restart,
     * and the steady-state cost of the filter stays a set lookup.
     */
    private final Set<UUID> provisioned = ConcurrentHashMap.newKeySet();

    private final UserProvisioningService userProvisioningService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ownerId().ifPresent(this::provisionOnce);

        chain.doFilter(request, response);
    }

    private void provisionOnce(UUID ownerId) {
        if (!provisioned.add(ownerId)) {
            return;
        }

        try {
            userProvisioningService.ensureProvisioned(ownerId);
        } catch (RuntimeException e) {
            // Let the next request try again rather than leaving the user half set up.
            provisioned.remove(ownerId);
            throw e;
        }
    }

    private Optional<UUID> ownerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(UUID.fromString(jwtAuthentication.getToken().getSubject()));
        }

        return Optional.empty();
    }
}
