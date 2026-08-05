package com.planelyx.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The editable part of a profile. Username is deliberately absent — the realm pins it. */
public record MeRequest(
        @NotBlank @Size(max = 255) String firstName,
        @Size(max = 255) String lastName,
        @NotBlank @Email @Size(max = 255) String email) {}
