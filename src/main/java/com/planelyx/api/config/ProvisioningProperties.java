package com.planelyx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The secret Keycloak's provisioning listener signs its callbacks with.
 *
 * Both sides read it from one key in the deployment's environment, so the value Keycloak signs
 * with and the value this application checks against cannot drift apart.
 */
@ConfigurationProperties(prefix = "planelyx.provisioning")
public record ProvisioningProperties(String webhookSecret) {}
