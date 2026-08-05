package com.planelyx.api.service;

import com.planelyx.api.domain.Category;
import com.planelyx.api.dto.CategoryRequest;
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
        return categoryRepository.findAllVisibleToOwner(ownerId);
    }

    public Category findById(UUID id, UUID ownerId) {
        return categoryRepository
                .findVisibleByIdAndOwner(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }

    private Category findOwnedById(UUID id, UUID ownerId) {
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
        Category category = findOwnedById(id, ownerId);

        category.setName(request.name());
        category.setType(request.type());
        category.setIcon(request.icon());
        category.setColor(request.color());

        return categoryRepository.save(category);
    }

    public void delete(UUID id, UUID ownerId) {
        Category category = findOwnedById(id, ownerId);

        categoryRepository.delete(category);
    }
}
