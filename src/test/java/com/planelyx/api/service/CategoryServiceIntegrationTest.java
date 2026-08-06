package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.exception.ForbiddenException;
import com.planelyx.api.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Category ownership.
 *
 * Categories used to be seeded once, globally, with no owner. That made them readable by everyone
 * but writable by no one, because the update and delete paths matched on the owner column. Now
 * every user is given their own copies, so the thing worth pinning is that a seeded category
 * behaves like any other category the user made — and that the two the application writes
 * corrections against still do not.
 */
class CategoryServiceIntegrationTest extends AbstractIntegrationTest {

    /** 18 defaults from V9 plus the two adjustment categories from V11. */
    private static final int SEEDED_COUNT = 20;

    @Autowired
    private CategoryService categoryService;

    @Test
    void aNewOwnerIsGivenTheirOwnCopyOfEverySeededCategory() {
        UUID ownerId = newOwner();

        List<Category> categories = categoryService.findAll(ownerId);

        assertEquals(SEEDED_COUNT, categories.size());
        assertTrue(
                categories.stream().allMatch(category -> ownerId.equals(category.getOwnerId())),
                "every seeded category belongs to the owner");
        assertEquals(2, categories.stream().filter(Category::isSystem).count(), "one adjustment category per type");
    }

    @Test
    void provisioningTwiceDoesNotDuplicateThem() {
        UUID ownerId = newOwner();

        userProvisioningService.ensureProvisioned(ownerId);

        assertEquals(SEEDED_COUNT, categoryService.findAll(ownerId).size());
    }

    @Test
    void ownersDoNotSeeEachOthersCategories() {
        UUID mine = newOwner();
        UUID theirs = newOwner();

        Category yours = firstOrdinary(theirs);

        assertThrows(NotFoundException.class, () -> categoryService.findById(yours.getId(), mine));
        assertThrows(NotFoundException.class, () -> categoryService.update(yours.getId(), rename("Hijacked"), mine));
    }

    /** The bug this all started from: a seeded default answered 404 to its own owner's edit. */
    @Test
    void aSeededCategoryCanBeRenamedByItsOwner() {
        UUID ownerId = newOwner();
        Category category = firstOrdinary(ownerId);

        categoryService.update(category.getId(), rename("Renamed"), ownerId);

        assertEquals(
                "Renamed", categoryService.findById(category.getId(), ownerId).getName());
    }

    @Test
    void aSeededCategoryCanBeDeletedByItsOwner() {
        UUID ownerId = newOwner();
        Category category = firstOrdinary(ownerId);

        categoryService.delete(category.getId(), ownerId);

        assertEquals(SEEDED_COUNT - 1, categoryService.findAll(ownerId).size());
        assertThrows(NotFoundException.class, () -> categoryService.findById(category.getId(), ownerId));
    }

    /**
     * Provisioning is keyed on the user having been set up, not on their having categories, so
     * clearing them out is a decision that sticks rather than one undone by the next request.
     */
    @Test
    void deletingEveryCategoryDoesNotSeedThemAgain() {
        UUID ownerId = newOwner();

        categoryService.findAll(ownerId).stream()
                .filter(category -> !category.isSystem())
                .forEach(category -> categoryService.delete(category.getId(), ownerId));

        userProvisioningService.ensureProvisioned(ownerId);

        List<Category> remaining = categoryService.findAll(ownerId);

        assertEquals(2, remaining.size(), "only the adjustment categories survive");
        assertTrue(remaining.stream().allMatch(Category::isSystem));
    }

    @Test
    void anAdjustmentCategoryMayNotBeEditedOrDeleted() {
        UUID ownerId = newOwner();
        Category adjustment = adjustmentCategory(ownerId, CategoryType.EXPENSE);

        assertThrows(
                ForbiddenException.class,
                () -> categoryService.update(adjustment.getId(), rename("Not an adjustment"), ownerId));
        assertThrows(ForbiddenException.class, () -> categoryService.delete(adjustment.getId(), ownerId));
    }

    @Test
    void anAdjustmentCategoryIsStillReadable() {
        UUID ownerId = newOwner();
        Category adjustment = adjustmentCategory(ownerId, CategoryType.INCOME);

        assertEquals(
                adjustment.getId(),
                categoryService.findById(adjustment.getId(), ownerId).getId());
    }

    @Test
    void anUnknownCategoryIsNotFound() {
        UUID ownerId = newOwner();
        UUID unknown = UUID.randomUUID();

        assertThrows(NotFoundException.class, () -> categoryService.update(unknown, rename("Nope"), ownerId));
        assertThrows(NotFoundException.class, () -> categoryService.delete(unknown, ownerId));
    }

    @Test
    void aCategoryTheUserMakesIsNotASystemOne() {
        UUID ownerId = newOwner();

        Category created =
                categoryService.create(new CategoryRequest("Electronics", CategoryType.EXPENSE, null, null), ownerId);

        assertFalse(created.isSystem());
    }

    private Category firstOrdinary(UUID ownerId) {
        return categoryService.findAll(ownerId).stream()
                .filter(category -> !category.isSystem())
                .findFirst()
                .orElseThrow();
    }

    private CategoryRequest rename(String name) {
        return new CategoryRequest(name, CategoryType.EXPENSE, null, null);
    }
}
