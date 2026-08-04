package br.com.planelyxapi.dto;

import java.math.BigDecimal;

/**
 * Totals over an entire filtered selection, independent of which page is being viewed.
 *
 * The transactions screen shows a net figure for the current filters. Once the list is paged
 * server-side the client only holds one page, so that figure cannot be derived there.
 */
public record TransactionSummaryResponse(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal net, long count) {}
