package br.com.planelyxapi.mapper;

import br.com.planelyxapi.domain.Category;
import br.com.planelyxapi.dto.CategoryResponse;

public final class CategoryMapper {

    private CategoryMapper() {}

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
