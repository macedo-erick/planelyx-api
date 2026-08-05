package com.planelyx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the API reaches Keycloak's admin endpoints, and as whom.
 *
 * Separate from {@code spring.security.oauth2...issuer-uri}, which is about verifying tokens
 * the UI presents. This is the other direction: the API calling Keycloak on its own behalf.
 */
@ConfigurationProperties(prefix = "planelyx.keycloak")
public record KeycloakAdminProperties(String serverUrl, String realm, String clientId, String clientSecret) {}
