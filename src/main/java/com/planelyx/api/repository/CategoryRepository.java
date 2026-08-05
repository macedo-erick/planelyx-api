package com.planelyx.api.repository;

import com.planelyx.api.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select c from Category c where c.ownerId is null or c.ownerId = :ownerId")
    List<Category> findAllVisibleToOwner(@Param("ownerId") UUID ownerId);

    @Query("select c from Category c where c.id = :id and (c.ownerId is null or c.ownerId = :ownerId)")
    Optional<Category> findVisibleByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
