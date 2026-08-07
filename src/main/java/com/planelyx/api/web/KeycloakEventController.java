package com.planelyx.api.web;

import com.planelyx.api.security.ProvisioningSignatureVerifier;
import com.planelyx.api.service.UserProvisioningService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives Keycloak's word that a user now exists, and sets that user up.
 *
 * Lives under {@code /internal} rather than {@code /api} on purpose: the production reverse proxy
 * routes {@code /ui/}, {@code /api/}, {@code /auth/}, {@code /actuator} and the API docs, and
 * nothing else. A path outside that set has no route from the internet at all, which leaves the
 * Compose network as the only way in. The signature check below is what makes that a second lock
 * rather than the only one.
 *
 * Delivery is at-least-once. A retry after a response was lost in flight arrives here as an exact
 * duplicate, so the work behind it has to be repeatable — see {@link UserProvisioningService}.
 */
@RestController
@RequestMapping("/internal/keycloak")
@RequiredArgsConstructor
@Slf4j
public class KeycloakEventController {

    /**
     * How far out a callback's own timestamp may be before it is refused. Wide enough for clock
     * skew between two containers and for the listener's last retry, narrow enough that a captured
     * request cannot be replayed indefinitely.
     */
    private static final Duration MAX_SKEW = Duration.ofMinutes(5);

    private final UserProvisioningService userProvisioningService;
    private final ProvisioningSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    /**
     * Takes the body as bytes, not a String: the signature covers exactly what was transmitted, and
     * decoding to text and back is a chance for the two to stop matching.
     */
    @PostMapping("/user-registered")
    public ResponseEntity<Void> userRegistered(
            @RequestHeader(value = "X-Planelyx-Signature", required = false) String signature,
            @RequestBody byte[] body) {

        if (!signatureVerifier.matches(body, signature)) {
            log.warn("Rejected a provisioning callback with a missing or invalid signature");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserRegistered event;

        try {
            event = objectMapper.readValue(body, UserRegistered.class);
        } catch (JacksonException e) {
            log.warn("Rejected a signed provisioning callback that could not be parsed: {}", e.getMessage());

            return ResponseEntity.badRequest().build();
        }

        if (event.userId() == null) {
            return ResponseEntity.badRequest().build();
        }

        long skew = Math.abs(System.currentTimeMillis() - event.timestamp());

        if (skew > MAX_SKEW.toMillis()) {
            log.warn("Rejected a provisioning callback for user {} that was {}ms out of date", event.userId(), skew);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        userProvisioningService.provision(event.userId());

        return ResponseEntity.accepted().build();
    }

    /** Unknown fields are ignored so the listener can add to the payload without a lockstep deploy. */
    record UserRegistered(UUID userId, String realm, long timestamp) {}
}
