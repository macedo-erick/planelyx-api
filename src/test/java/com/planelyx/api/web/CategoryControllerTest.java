package com.planelyx.api.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.security.SecurityConfig;
import com.planelyx.api.service.CategoryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CurrentUser.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsCategoriesForAuthenticatedUser() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Category category = Category.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build();
        when(categoryService.findAll(ownerId)).thenReturn(List.of(category));

        mockMvc.perform(get("/api/categories").with(jwt().jwt(jwt -> jwt.subject(ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Groceries"));
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        UUID ownerId = UUID.randomUUID();
        mockMvc.perform(post("/api/categories")
                        .with(jwt().jwt(jwt -> jwt.subject(ownerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
