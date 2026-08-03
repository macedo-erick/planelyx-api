package br.com.fintrackapi.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.domain.enums.AccountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BankAccountRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Test
    void savesAndFindsByOwner() {
        UUID ownerId = UUID.randomUUID();
        BankAccount account = BankAccount.builder()
                .ownerId(ownerId)
                .name("Checking")
                .bankName("Bank of Test")
                .accountType(AccountType.CHECKING)
                .initialBalance(BigDecimal.TEN)
                .currency("BRL")
                .active(true)
                .build();

        bankAccountRepository.save(account);

        List<BankAccount> found = bankAccountRepository.findAllByOwnerId(ownerId);
        assertEquals(1, found.size());
        assertEquals("Checking", found.getFirst().getName());
    }
}
