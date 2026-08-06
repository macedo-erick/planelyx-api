package com.planelyx.api.mapper;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.SystemCategories;
import com.planelyx.api.dto.CategoryResponse;

public final class CategoryMapper {

    private CategoryMapper() {}

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.getColor(),
                SystemCategories.contains(category.getId()),
                category.getCreatedAt());
    }
}
