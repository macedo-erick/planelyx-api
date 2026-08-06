package com.planelyx.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.enums.CategoryType;
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
class CategoryRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private CategoryRepository categoryRepository;

    /** The list an owner picks from: no adjustment category, ordered EXPENSE before INCOME and A-Z within each. */
    @Test
    void findsVisibleCategoriesSortedByTypeThenName() {
        UUID ownerId = UUID.randomUUID();
        categoryRepository.save(category(ownerId, "Salary", CategoryType.INCOME, false));
        categoryRepository.save(category(ownerId, "rent", CategoryType.EXPENSE, false));
        categoryRepository.save(category(ownerId, "Groceries", CategoryType.EXPENSE, false));
        categoryRepository.save(category(ownerId, "Adjustment", CategoryType.EXPENSE, true));

        List<Category> visible = categoryRepository.findVisibleByOwnerId(ownerId);

        assertEquals(List.of("Groceries", "rent", "Salary"), visible.stream().map(Category::getName).toList());
    }

    private Category category(UUID ownerId, String name, CategoryType type, boolean system) {
        return Category.builder()
                .ownerId(ownerId)
                .name(name)
                .type(type)
                .system(system)
                .build();
    }
}
