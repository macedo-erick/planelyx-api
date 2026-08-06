package com.planelyx.api.web;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planelyx.api.security.ProvisioningSignatureVerifier;
import com.planelyx.api.security.SecurityConfig;
import com.planelyx.api.service.UserProvisioningService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The one endpoint in the application that answers without a bearer token, so what it refuses
 * matters as much as what it accepts.
 *
 * The signature is checked here; that a repeated callback does not duplicate anything is a
 * property of the seeding query, and is pinned by
 * {@code CategoryServiceIntegrationTest.provisioningTwiceDoesNotDuplicateThem}.
 */
@WebMvcTest(KeycloakEventController.class)
@Import({SecurityConfig.class, ProvisioningSignatureVerifier.class})
// Set as a property rather than as a bean: PlanelyxApplication already registers
// ProvisioningProperties via @EnableConfigurationProperties, and a second one of the same type
// makes the injection ambiguous.
@TestPropertySource(properties = "planelyx.provisioning.webhook-secret=" + KeycloakEventControllerTest.SECRET)
class KeycloakEventControllerTest {

    static final String SECRET = "test-provisioning-secret";

    private static final String SIGNATURE_HEADER = "X-Planelyx-Signature";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProvisioningService userProvisioningService;

    @Test
    void provisionsTheUserWhenTheSignatureMatches() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = payload(userId, System.currentTimeMillis());

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(body))
                        .content(body))
                .andExpect(status().isAccepted());

        verify(userProvisioningService).provision(userId);
    }

    /** Delivery is at-least-once, so the endpoint has to keep accepting the same call. */
    @Test
    void acceptsARedeliveryOfTheSameCallback() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = payload(userId, System.currentTimeMillis());
        String signature = sign(body);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/internal/keycloak/user-registered")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, signature)
                            .content(body))
                    .andExpect(status().isAccepted());
        }

        verify(userProvisioningService, times(2)).provision(userId);
    }

    @Test
    void refusesATamperedSignature() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = payload(userId, System.currentTimeMillis());
        String signature = sign(body);
        // Flip the last hex digit, whatever it happens to be.
        String tampered = signature.substring(0, signature.length() - 1) + (signature.endsWith("0") ? '1' : '0');

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, tampered)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProvisioningService);
    }

    /** A body swapped after signing must not pass, or the signature protects nothing. */
    @Test
    void refusesABodyThatDoesNotMatchItsSignature() throws Exception {
        String signed = payload(UUID.randomUUID(), System.currentTimeMillis());
        String sent = payload(UUID.randomUUID(), System.currentTimeMillis());

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(signed))
                        .content(sent))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProvisioningService);
    }

    @Test
    void refusesAnUnsignedCallback() throws Exception {
        String body = payload(UUID.randomUUID(), System.currentTimeMillis());

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProvisioningService);
    }

    /** Bounds how long a captured callback stays usable if one ever leaks. */
    @Test
    void refusesACallbackThatIsTooOld() throws Exception {
        long stale = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis();
        String body = payload(UUID.randomUUID(), stale);

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(body))
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProvisioningService);
    }

    @Test
    void refusesASignedBodyWithoutAUserId() throws Exception {
        String body = "{\"realm\":\"planelyx\",\"timestamp\":" + System.currentTimeMillis() + "}";

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(body))
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProvisioningService);
    }

    @Test
    void refusesASignedBodyThatIsNotJson() throws Exception {
        String body = "not json";

        mockMvc.perform(post("/internal/keycloak/user-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, sign(body))
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProvisioningService);
    }

    private static String payload(UUID userId, long timestamp) {
        return "{\"userId\":\"%s\",\"realm\":\"planelyx\",\"timestamp\":%d}".formatted(userId, timestamp);
    }

    /** Deliberately a second implementation of the HMAC, not a call into the verifier. */
    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
