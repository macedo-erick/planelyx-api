package com.planelyx.api;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.repository.CategoryRepository;
import com.planelyx.api.service.UserProvisioningService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected UserProvisioningService userProvisioningService;

    @Autowired
    protected CategoryRepository categoryRepository;

    /**
     * An owner set up the way a real one is.
     *
     * Tests drive the services directly, so nothing else would run the provisioning that normally
     * happens on a user's first authenticated request — and without it the owner has no categories
     * at all, including the adjustment ones the corrections need.
     */
    protected UUID newOwner() {
        UUID ownerId = UUID.randomUUID();
        userProvisioningService.ensureProvisioned(ownerId);

        return ownerId;
    }

    protected Category adjustmentCategory(UUID ownerId, CategoryType type) {
        return categoryRepository
                .findAdjustmentForOwner(ownerId, type)
                .orElseThrow(() -> new AssertionError("No " + type + " adjustment category for owner " + ownerId));
    }
}
