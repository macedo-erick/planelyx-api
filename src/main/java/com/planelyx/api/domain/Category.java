package com.planelyx.api.domain;

import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.SystemCategoryKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A category, whether the user's own or one the application owns.
 *
 * {@code system} marks the latter. Every user gets their own copy of the system categories, so the
 * flag is what identifies one: it is why the API refuses to let a user edit, delete, or hand-file a
 * transaction against it, and how a client knows to keep it out of its picker while still rendering
 * it on the rows that use it. {@code systemKey} says which one it is, so the app can look one up by
 * role rather than by name — the flag alone stopped being enough once there was more than one
 * system category of the same type, and a name cannot stand in for it, being the thing a client
 * translates.
 */
@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;

    private String icon;

    private String color;

    @Column(nullable = false)
    private boolean system;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_key")
    private SystemCategoryKey systemKey;
}
