package com.planelyx.api.repository;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.enums.CategoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Category> findByOwnerId(UUID ownerId);

    /**
     * The categories an owner picks from: no adjustment categories, sorted for display so no
     * caller needs to order them again.
     */
    @Query("select c from Category c where c.ownerId = :ownerId order by c.type asc, lower(c.name) asc")
    List<Category> findVisibleByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * The category the application files this owner's corrections against.
     *
     * Single-valued because {@code uq_category_template_system_type} allows one system template
     * per type, and every user gets one copy of each.
     */
    @Query("select c from Category c where c.ownerId = :ownerId and c.system = true and c.type = :type")
    Optional<Category> findAdjustmentForOwner(@Param("ownerId") UUID ownerId, @Param("type") CategoryType type);

    /**
     * Gives an owner their own copy of every seeded category, once.
     *
     * The {@code not exists} clause is what makes a repeated provisioning callback harmless:
     * Keycloak's listener retries until it sees a 2xx, so a response lost after this committed
     * arrives again as an identical request, and without the guard that second run would duplicate
     * every category. It is a guard against redelivery, not a general "seed if empty" — the only
     * thing that ever calls this is registration.
     */
    @Modifying
    @Query(value = """
                    insert into category (id, owner_id, name, type, icon, color, system)
                    select gen_random_uuid(), :ownerId, name, type, icon, color, system
                    from category_template
                    where not exists (select 1 from category c where c.owner_id = :ownerId)
                    """, nativeQuery = true)
    int copyTemplatesFor(@Param("ownerId") UUID ownerId);
}
