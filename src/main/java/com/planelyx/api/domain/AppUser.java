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
 * A user the application has seen at least once.
 *
 * The profile itself lives in Keycloak — this only records the {@code sub}, so that setting a
 * user up (currently: giving them their own copy of the seeded categories) happens exactly once.
 * Without a row to write, "seed if they have no categories" would be the only test available,
 * and a user who deleted all of theirs would get them back.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
