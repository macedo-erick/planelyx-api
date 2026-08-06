package com.planelyx.api.security;

import com.planelyx.api.config.ProvisioningProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Checks that a provisioning callback really came from Keycloak.
 *
 * The endpoint this guards is the only unauthenticated write in the application, so the comparison
 * is done over the raw request bytes and in constant time. It is not the only thing standing in
 * front of that endpoint — the reverse proxy does not route {@code /internal}, so the callback is
 * reachable only from inside the Compose network — but it is the part that does not depend on the
 * proxy configuration staying correct.
 */
@Component
public class ProvisioningSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private static final String PREFIX = "sha256=";

    private final byte[] secret;

    public ProvisioningSignatureVerifier(ProvisioningProperties properties) {
        this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean matches(byte[] body, String presented) {
        if (presented == null || !presented.startsWith(PREFIX)) {
            return false;
        }

        // Compared as bytes rather than with String.equals, which returns early on the first
        // differing character and so leaks how much of a guessed signature was right.
        return MessageDigest.isEqual(
                expected(body).getBytes(StandardCharsets.US_ASCII), presented.getBytes(StandardCharsets.US_ASCII));
    }

    private String expected(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));

            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is required of every JRE, so this only fires on an empty secret.
            throw new IllegalStateException("Could not verify the provisioning signature", e);
        }
    }
}
