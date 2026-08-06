package com.planelyx.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An owner whose one-time setup has already been done.
 *
 * Not a user table, despite being keyed by the Keycloak {@code sub} — the profile lives in
 * Keycloak and nothing about the person is stored here. All this records is that the application
 * has already set this owner up (currently: given them their own copy of the seeded categories),
 * so that it never does it twice.
 *
 * Storing that as a row is what makes the guarantee hold. Without it the only test available
 * would be "do they have any categories", and a user who deliberately deleted all of theirs
 * would get them back on their next request.
 */
@Entity
@Table(name = "provisioned_owner")
@Getter
@NoArgsConstructor
public class ProvisionedOwner {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
