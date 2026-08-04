package br.com.planelyxapi.dto;

import br.com.planelyxapi.domain.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryRequest(
        @NotBlank String name, @NotNull CategoryType type, String icon, String color) {}
