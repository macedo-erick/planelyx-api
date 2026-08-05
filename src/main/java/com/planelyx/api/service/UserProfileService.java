package com.planelyx.api.service;

import com.planelyx.api.dto.MeRequest;
import com.planelyx.api.dto.MeResponse;
import com.planelyx.api.security.KeycloakAdminClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The signed-in user's own profile.
 *
 * The profile lives in Keycloak, not in this database — the app only ever stores the {@code sub}
 * as an owner id. Editing it therefore means calling Keycloak rather than writing a row, which
 * is why there is no entity or repository behind this.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final KeycloakAdminClient keycloak;

    public MeResponse find(UUID userId) {
        return toResponse(keycloak.findUser(userId));
    }

    /**
     * Changing an email clears its verified flag, so a user cannot promote an unverified
     * address to a verified one by editing it.
     */
    public MeResponse update(UUID userId, MeRequest request) {
        Map<String, Object> current = keycloak.findUser(userId);
        String email = request.email().trim();
        boolean emailChanged = !email.equalsIgnoreCase(string(current.get("email")));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("firstName", request.firstName().trim());
        attributes.put(
                "lastName", Objects.requireNonNullElse(request.lastName(), "").trim());
        attributes.put("email", email);

        if (emailChanged) {
            attributes.put("emailVerified", false);
        }

        keycloak.updateUser(userId, attributes);

        return find(userId);
    }

    private MeResponse toResponse(Map<String, Object> user) {
        return new MeResponse(
                string(user.get("username")),
                string(user.get("firstName")),
                string(user.get("lastName")),
                string(user.get("email")),
                Boolean.TRUE.equals(user.get("emailVerified")));
    }

    private String string(Object value) {
        return value instanceof String text ? text : "";
    }
}
