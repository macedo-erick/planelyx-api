package br.com.fintrackapi.dto;

import br.com.fintrackapi.domain.CategoryType;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType type,
        String icon,
        String color,
        Instant createdAt) {
}
