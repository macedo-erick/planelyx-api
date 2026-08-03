package br.com.fintrackapi.service;

import java.time.LocalDate;

public record BillingPeriod(LocalDate start, LocalDate end, LocalDate dueDate) {}
