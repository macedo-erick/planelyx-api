package com.planelyx.api.repository;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.SystemCategoryKey;
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
     * Every category an owner has, system ones included, sorted for display so no caller needs to
     * order them again.
     *
     * The system categories are deliberately here. A client resolves a transaction's category name,
     * icon and colour against this list, so holding them back would leave every adjustment and
     * every invoice payment rendering with no category at all. Keeping them out of the *picker* is
     * the client's job — it has the {@code system} flag to do it with, and the API refuses the
     * write anyway.
     */
    @Query("select c from Category c where c.ownerId = :ownerId order by c.type asc, lower(c.name) asc")
    List<Category> findVisibleByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * The category the application files a given kind of its own writing against.
     *
     * Keyed on the role as well as the type. The role alone is not enough — an adjustment exists
     * in both an EXPENSE and an INCOME flavour, because a correction can push a balance either
     * way — and the type alone stopped being enough once settlements needed a category too.
     *
     * Single-valued because {@code uq_category_template_system_key} allows one template per
     * (role, type) and every owner gets one copy of each.
     */
    Optional<Category> findByOwnerIdAndSystemKeyAndType(UUID ownerId, SystemCategoryKey systemKey, CategoryType type);

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
                    insert into category (id, owner_id, name, type, icon, color, system, system_key)
                    select gen_random_uuid(), :ownerId, name, type, icon, color, system, system_key
                    from category_template
                    where not exists (select 1 from category c where c.owner_id = :ownerId)
                    """, nativeQuery = true)
    int copyTemplatesFor(@Param("ownerId") UUID ownerId);
}
