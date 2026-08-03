package br.com.fintrackapi.repository;

import br.com.fintrackapi.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOwnerId(UUID ownerId);

    Optional<Category> findByIdAndOwnerId(UUID id, UUID ownerId);
}
