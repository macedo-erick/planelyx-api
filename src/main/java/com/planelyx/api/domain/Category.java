package com.planelyx.api.domain;

import com.planelyx.api.domain.enums.CategoryType;
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

    /**
     * Whether the application owns this category rather than the user. Every user gets their own
     * copy of the adjustment categories, so the flag is what identifies one — it is both how the
     * corrections in {@link com.planelyx.api.service.BalanceAdjustmentService} and
     * {@link com.planelyx.api.service.InvoiceService} find the category to file against, and why
     * the API refuses to let a user edit, delete, or hand-file a transaction against it.
     */
    @Column(nullable = false)
    private boolean system;
}
