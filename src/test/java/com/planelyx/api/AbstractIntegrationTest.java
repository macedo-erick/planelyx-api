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
     * In production this is driven by Keycloak's registration callback. Tests have no Keycloak, so
     * they call the same service directly — without it the owner has no categories at all,
     * including the adjustment ones the corrections need.
     */
    protected UUID newOwner() {
        UUID ownerId = UUID.randomUUID();
        userProvisioningService.provision(ownerId);

        return ownerId;
    }

    protected Category adjustmentCategory(UUID ownerId, CategoryType type) {
        return categoryRepository
                .findAdjustmentForOwner(ownerId, type)
                .orElseThrow(() -> new AssertionError("No " + type + " adjustment category for owner " + ownerId));
    }
}
