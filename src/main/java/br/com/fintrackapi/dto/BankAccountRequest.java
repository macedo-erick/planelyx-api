package br.com.fintrackapi.dto;

import br.com.fintrackapi.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BankAccountRequest(
        @NotBlank String name,
        @NotBlank String bankName,
        @NotNull AccountType accountType,
        @NotNull BigDecimal initialBalance,
        @NotBlank String currency) {
}
