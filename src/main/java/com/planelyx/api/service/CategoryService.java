package com.planelyx.api.service;

import com.planelyx.api.domain.Category;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.exception.ForbiddenException;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll(UUID ownerId) {
        return categoryRepository.findByOwnerId(ownerId);
    }

    /** The categories an owner can pick from, i.e. everything but the adjustment ones. */
    public List<Category> findVisible(UUID ownerId) {
        return categoryRepository.findVisibleByOwnerId(ownerId);
    }

    public Category findById(UUID id, UUID ownerId) {
        return categoryRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }

    public Category create(CategoryRequest request, UUID ownerId) {
        Category category = Category.builder()
                .ownerId(ownerId)
                .name(request.name())
                .type(request.type())
                .icon(request.icon())
                .color(request.color())
                .build();

        return categoryRepository.save(category);
    }

    public Category update(UUID id, CategoryRequest request, UUID ownerId) {
        Category category = findWritableById(id, ownerId);

        category.setName(request.name());
        category.setType(request.type());
        category.setIcon(request.icon());
        category.setColor(request.color());

        return categoryRepository.save(category);
    }

    public void delete(UUID id, UUID ownerId) {
        Category category = findWritableById(id, ownerId);

        categoryRepository.delete(category);
    }

    /**
     * A category of the owner's that they are also allowed to change.
     *
     * The only ones they are not are the adjustment categories: the application writes balance and
     * invoice corrections against them, so renaming one would relabel history it had reconciled,
     * and deleting one would leave the next correction with nowhere to go.
     */
    private Category findWritableById(UUID id, UUID ownerId) {
        Category category = findById(id, ownerId);

        if (category.isSystem()) {
            throw new ForbiddenException("Adjustment categories cannot be modified: " + id);
        }

        return category;
    }
}
