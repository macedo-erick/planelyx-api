package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.CategoryType;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, CategoryType type, String icon, String color, Instant createdAt) {}
