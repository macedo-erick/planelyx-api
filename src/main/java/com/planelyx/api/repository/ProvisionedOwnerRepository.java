package com.planelyx.api.repository;

import com.planelyx.api.domain.ProvisionedOwner;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProvisionedOwnerRepository extends JpaRepository<ProvisionedOwner, UUID> {

    /**
     * Registers an owner as set up, reporting whether this call was the one that did it.
     *
     * {@code ON CONFLICT DO NOTHING} makes the claim atomic, so two concurrent first requests from
     * the same user cannot both conclude they are the first and seed twice.
     *
     * @return 1 if the owner was new, 0 if they had already been set up
     */
    @Modifying
    @Query(value = "insert into provisioned_owner (id) values (:id) on conflict (id) do nothing", nativeQuery = true)
    int claim(@Param("id") UUID id);
}
