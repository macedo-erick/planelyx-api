package com.planelyx.api.security;

import com.planelyx.api.config.KeycloakAdminProperties;
import com.planelyx.api.exception.ConflictException;
import com.planelyx.api.exception.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * The slice of Keycloak's Admin API the app needs: reading and updating one user.
 *
 * Written against {@link RestClient} rather than pulling in {@code keycloak-admin-client},
 * which brings a large dependency tree for two calls and pins its own Keycloak version.
 *
 * Callers are expected to pass the {@code sub} of the authenticated principal — nothing here
 * checks who is asking, so exposing it for any other id would let one user edit another.
 */
@Component
public class KeycloakAdminClient {

    /** Renewed this far before expiry so a call never races the token going stale. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final KeycloakAdminProperties properties;
    private final RestClient restClient;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public KeycloakAdminClient(KeycloakAdminProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.serverUrl()).build();
    }

    public Map<String, Object> findUser(UUID userId) {
        return restClient
                .get()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .onStatus(status -> status == HttpStatus.NOT_FOUND, (request, response) -> {
                    throw new NotFoundException("User not found: " + userId);
                })
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * A partial update — Keycloak merges the attributes present and leaves the rest alone, so
     * the caller only sends what it means to change.
     */
    public void updateUser(UUID userId, Map<String, Object> attributes) {
        restClient
                .put()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(attributes)
                .retrieve()
                // Keycloak answers 409 when the email or username is already taken by someone
                // else. That is the user's mistake to correct, not a server fault.
                .onStatus(status -> status == HttpStatus.CONFLICT, (request, response) -> {
                    throw new ConflictException("That email address is already in use");
                })
                .toBodilessEntity();
    }

    /** A client-credentials token for the service account, reused until it is nearly expired. */
    private String accessToken() {
        CachedToken current = cachedToken.get();

        if (current != null && current.isUsable()) {
            return current.value();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response = restClient
                .post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null) {
            throw new IllegalStateException("Keycloak returned no token for the service account");
        }

        CachedToken fresh =
                new CachedToken(response.access_token(), Instant.now().plusSeconds(response.expires_in()));

        cachedToken.set(fresh);

        return fresh.value();
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isUsable() {
            return Instant.now().isBefore(expiresAt.minus(EXPIRY_MARGIN));
        }
    }

    /** Field names are Keycloak's, so they stay snake_case rather than being remapped. */
    @SuppressWarnings("java:S116")
    private record TokenResponse(String access_token, long expires_in) {}
}
