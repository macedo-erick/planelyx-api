package br.com.planelyxapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.planelyxapi.AbstractIntegrationTest;
import br.com.planelyxapi.domain.BankAccount;
import br.com.planelyxapi.domain.Category;
import br.com.planelyxapi.domain.CreditCard;
import br.com.planelyxapi.domain.Transaction;
import br.com.planelyxapi.domain.TransactionTemplate;
import br.com.planelyxapi.domain.enums.AccountType;
import br.com.planelyxapi.domain.enums.CategoryType;
import br.com.planelyxapi.domain.enums.RecurrenceType;
import br.com.planelyxapi.domain.enums.TransactionKind;
import br.com.planelyxapi.domain.enums.TransactionScope;
import br.com.planelyxapi.dto.BankAccountRequest;
import br.com.planelyxapi.dto.CategoryRequest;
import br.com.planelyxapi.dto.CreditCardRequest;
import br.com.planelyxapi.dto.TransactionRequest;
import br.com.planelyxapi.dto.TransactionSummaryResponse;
import br.com.planelyxapi.dto.TransactionTemplateRequest;
import br.com.planelyxapi.dto.TransactionUpdateRequest;
import br.com.planelyxapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class TransactionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private TransactionTemplateService transactionTemplateService;

    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * The paged lookup issues a count query alongside the data query, and the spec carries a
     * left join fetch of the template. A fetch join is illegal in a count query, so without the
     * guard in TransactionService#fetchTemplate this throws rather than returning a page.
     */
    @Test
    void pagesWithoutTrippingOverTheTemplateFetchJoin() {
        Fixture fixture = seed(5);

        Page<Transaction> firstPage = transactionService.findAll(
                fixture.ownerId(),
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 2, TransactionService.NEWEST_FIRST));

        assertEquals(2, firstPage.getContent().size());
        assertEquals(5, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
    }

    /** Same-day rows need a total ordering, or a row can land on two pages or on none. */
    @Test
    void pagingIsStableAcrossRowsSharingATransactionDate() {
        Fixture fixture = seedOnSingleDay(6);

        List<UUID> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            transactionService
                    .findAll(
                            fixture.ownerId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            PageRequest.of(page, 2, TransactionService.NEWEST_FIRST))
                    .forEach(transaction -> seen.add(transaction.getId()));
        }

        assertEquals(6, seen.size());
        assertEquals(6, Set.copyOf(seen).size(), "a row must not repeat across pages");
    }

    @Test
    void filtersByKindServerSide() {
        Fixture fixture = seed(3);

        Page<Transaction> credits = transactionService.findAll(
                fixture.ownerId(),
                null,
                null,
                null,
                TransactionKind.ACCOUNT_CREDIT,
                null,
                null,
                PageRequest.of(0, 25, TransactionService.NEWEST_FIRST));

        assertEquals(1, credits.getTotalElements());
        assertTrue(credits.getContent().stream().allMatch(t -> t.getKind() == TransactionKind.ACCOUNT_CREDIT));
    }

    /** The summary spans the whole selection, not the page the client happens to hold. */
    @Test
    void summarizesTheWholeSelectionRegardlessOfPaging() {
        Fixture fixture = seed(3);

        TransactionSummaryResponse summary =
                transactionService.summarize(fixture.ownerId(), null, null, null, null, null, null);

        // seed(3): one 100.00 credit, two 10.00 debits.
        assertEquals(0, new BigDecimal("100.00").compareTo(summary.totalIncome()));
        assertEquals(0, new BigDecimal("20.00").compareTo(summary.totalExpense()));
        assertEquals(0, new BigDecimal("80.00").compareTo(summary.net()));
        assertEquals(3, summary.count());
    }

    @Test
    void deletingASingleInstallmentLeavesItsSiblings() {
        Series series = seedInstallments(3);

        transactionService.delete(series.middle().getId(), series.ownerId(), TransactionScope.SINGLE);

        List<Transaction> left = remaining(series);
        assertEquals(2, left.size());
        assertFalse(
                left.stream().anyMatch(t -> t.getId().equals(series.middle().getId())),
                "the targeted installment is the one that goes");
    }

    @Test
    void deletingFutureKeepsEarlierInstallments() {
        Series series = seedInstallments(3);

        transactionService.delete(series.middle().getId(), series.ownerId(), TransactionScope.FUTURE);

        List<Transaction> left = remaining(series);
        assertEquals(1, left.size(), "only the installment before the target survives");
        assertTrue(left.getFirst().getTransactionDate().isBefore(series.middle().getTransactionDate()));
    }

    @Test
    void deletingAllClearsTheSeriesAndStopsTheGenerator() {
        Series series = seedInstallments(3);

        transactionService.delete(series.middle().getId(), series.ownerId(), TransactionScope.ALL);

        assertEquals(0, remaining(series).size());
        assertFalse(
                transactionTemplateService
                        .findById(series.templateId(), series.ownerId())
                        .isActive(),
                "the template must be deactivated so the top-up job cannot regenerate the series");
    }

    /**
     * Amount and description spread across the series, but the date must not — moving every
     * sibling onto the target's date would collapse a monthly series onto one day.
     */
    @Test
    void updatingAllSpreadsFieldsButMovesOnlyTheTargetDate() {
        Series series = seedInstallments(3);
        LocalDate movedTo = LocalDate.of(2026, 12, 25);
        List<LocalDate> datesBefore = remaining(series).stream()
                .map(Transaction::getTransactionDate)
                .sorted()
                .toList();

        transactionService.update(
                series.middle().getId(),
                new TransactionUpdateRequest(
                        series.categoryId(), new BigDecimal("42.00"), movedTo, "Renamed", TransactionScope.ALL),
                series.ownerId());

        List<Transaction> after = remaining(series);
        assertEquals(3, after.size());
        assertTrue(after.stream().allMatch(t -> t.getDescription().equals("Renamed")));
        assertTrue(after.stream().allMatch(t -> new BigDecimal("42.00").compareTo(t.getAmount()) == 0));

        List<LocalDate> datesAfter =
                after.stream().map(Transaction::getTransactionDate).sorted().toList();
        assertEquals(1, countDifferences(datesBefore, datesAfter), "only the edited row may move");
        assertTrue(datesAfter.contains(movedTo));
    }

    private long countDifferences(List<LocalDate> before, List<LocalDate> after) {
        List<LocalDate> leftover = new ArrayList<>(before);
        after.forEach(leftover::remove);
        return leftover.size();
    }

    private List<Transaction> remaining(Series series) {
        return transactionRepository.findAllByTemplateId(series.templateId());
    }

    private record Series(UUID ownerId, UUID templateId, UUID categoryId, Transaction middle) {}

    /** An installment template of {@code count} monthly card charges, plus its middle occurrence. */
    private Series seedInstallments(int count) {
        UUID ownerId = UUID.randomUUID();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, BigDecimal.ZERO, "BRL"), ownerId);

        CreditCard card = creditCardService.create(
                new CreditCardRequest(account.getId(), "Gold", "VISA", new BigDecimal("5000.00"), 10, 17), ownerId);

        Category category =
                categoryService.create(new CategoryRequest("Electronics", CategoryType.EXPENSE, null, null), ownerId);

        TransactionTemplate template = transactionTemplateService.create(
                new TransactionTemplateRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        category.getId(),
                        "Laptop",
                        new BigDecimal("300.00"),
                        RecurrenceType.INSTALLMENT,
                        LocalDate.of(2026, 8, 15),
                        count),
                ownerId);

        List<Transaction> occurrences = transactionRepository.findAllByTemplateId(template.getId()).stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .toList();

        return new Series(ownerId, template.getId(), category.getId(), occurrences.get(count / 2));
    }

    private record Fixture(UUID ownerId, BankAccount account, Category category) {}

    /** One ACCOUNT_CREDIT of 100.00, then {@code count - 1} debits of 10.00 on separate days. */
    private Fixture seed(int count) {
        Fixture fixture = base();

        transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_CREDIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal("100.00"),
                        LocalDate.of(2026, 8, 1),
                        "Salary"),
                fixture.ownerId());

        for (int i = 1; i < count; i++) {
            transactionService.create(
                    new TransactionRequest(
                            TransactionKind.ACCOUNT_DEBIT,
                            fixture.account().getId(),
                            null,
                            fixture.category().getId(),
                            new BigDecimal("10.00"),
                            LocalDate.of(2026, 8, 1).plusDays(i),
                            "Spend " + i),
                    fixture.ownerId());
        }

        return fixture;
    }

    private Fixture seedOnSingleDay(int count) {
        Fixture fixture = base();

        for (int i = 0; i < count; i++) {
            transactionService.create(
                    new TransactionRequest(
                            TransactionKind.ACCOUNT_DEBIT,
                            fixture.account().getId(),
                            null,
                            fixture.category().getId(),
                            new BigDecimal("10.00"),
                            LocalDate.of(2026, 8, 1),
                            "Same day " + i),
                    fixture.ownerId());
        }

        return fixture;
    }

    private Fixture base() {
        UUID ownerId = UUID.randomUUID();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, BigDecimal.ZERO, "BRL"), ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        return new Fixture(ownerId, account, category);
    }
}
