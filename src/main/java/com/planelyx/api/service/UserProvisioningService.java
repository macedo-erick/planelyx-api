package com.planelyx.api.service;

import com.planelyx.api.repository.CategoryRepository;
import com.planelyx.api.repository.ProvisionedOwnerRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets a user up the first time the application sees them.
 *
 * Keycloak does have a registration hook — the Event Listener SPI fires {@code REGISTER} once per
 * new user — but consuming it would mean shipping a custom provider jar into planelyx-auth's
 * independently built image and hand-creating a client in a production realm that never
 * re-imports. Seeding on the first authenticated request costs a set lookup instead, and needs
 * nothing outside this repository.
 *
 * Keeping the marker in Postgres rather than on the Keycloak user is what makes this safe:
 * {@code claim} and the copy below are one transaction against one database, so either both
 * happen or neither. Split across two systems they could not be, and a copy that committed while
 * the flag write failed would re-seed on the next request — handing back the categories of a user
 * who had deliberately deleted them.
 *
 * That is also why the test is "have we set this user up" rather than "do they have any
 * categories": a user who deletes every category they own keeps them deleted.
 */
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final ProvisionedOwnerRepository provisionedOwnerRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public void ensureProvisioned(UUID ownerId) {
        if (provisionedOwnerRepository.claim(ownerId) == 0) {
            return;
        }

        categoryRepository.copyTemplatesFor(ownerId);
    }
}
