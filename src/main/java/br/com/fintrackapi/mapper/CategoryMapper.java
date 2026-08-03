package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.Category;
import br.com.fintrackapi.dto.CategoryResponse;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.getColor(),
                category.getCreatedAt());
    }
}
