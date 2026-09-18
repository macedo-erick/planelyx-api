package com.planelyx.api.domain;

import com.planelyx.api.domain.enums.InvestmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Somewhere other than a bank account for money to sit, with a balance derived from its movements
 * exactly as {@link BankAccount}'s is. {@code initialBalance} records one the owner already held.
 */
@Entity
@Table(name = "investment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Investment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_type", nullable = false)
    private InvestmentType investmentType;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private boolean active;
}
