package com.planelyx.api.service;

import com.planelyx.api.repository.AppUserRepository;
import com.planelyx.api.repository.CategoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets a user up the first time the application sees them.
 *
 * Keycloak creates users, and it does not tell the API when it does, so there is no registration
 * hook to seed from — provisioning happens on the first authenticated request instead. The
 * {@code app_user} row is what makes it happen only once: a user who deletes every category they
 * own keeps them deleted, because the test is "have we set this user up", not "do they have any
 * categories".
 */
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final AppUserRepository appUserRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public void ensureProvisioned(UUID ownerId) {
        if (appUserRepository.claim(ownerId) == 0) {
            return;
        }

        categoryRepository.copyTemplatesFor(ownerId);
    }
}
