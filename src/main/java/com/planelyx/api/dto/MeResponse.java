package com.planelyx.api.dto;

/**
 * The signed-in user's own profile, as Keycloak holds it.
 *
 * {@code username} is read-only: the realm does not enable {@code editUsername}, so Keycloak
 * would reject a change to it.
 */
public record MeResponse(String username, String firstName, String lastName, String email, boolean emailVerified) {}
