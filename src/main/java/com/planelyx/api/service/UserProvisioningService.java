package com.planelyx.api.service;

import com.planelyx.api.repository.CategoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives a new user their own copy of the seeded categories.
 *
 * Driven by Keycloak's {@code REGISTER} event rather than by the first request the user makes, so
 * this runs once, when the user is created, and nothing has to be checked on the way into every
 * other endpoint.
 *
 * The trade that comes with it: the event is delivered over the network and can be delivered more
 * than once, or — past the listener's retries — not at all. Repeat delivery is handled by
 * {@code copyTemplatesFor}, which does nothing for an owner who already has categories. Total loss
 * is not handled, and cannot be: there is no second trigger. That is also what keeps a user who
 * deletes every category from being handed them back, since nothing after registration ever seeds
 * again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public void provision(UUID ownerId) {
        int seeded = categoryRepository.copyTemplatesFor(ownerId);

        log.info("Provisioned owner {}: {} categories seeded", ownerId, seeded);
    }
}
