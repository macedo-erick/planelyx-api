package br.com.planelyxapi.web;

import br.com.planelyxapi.dto.CategoryRequest;
import br.com.planelyxapi.dto.CategoryResponse;
import br.com.planelyxapi.mapper.CategoryMapper;
import br.com.planelyxapi.security.CurrentUser;
import br.com.planelyxapi.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<CategoryResponse> findAll() {
        return categoryService.findAll(currentUser.ownerId()).stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public CategoryResponse findById(@PathVariable UUID id) {
        return CategoryMapper.toResponse(categoryService.findById(id, currentUser.ownerId()));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = CategoryMapper.toResponse(categoryService.create(request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return CategoryMapper.toResponse(categoryService.update(id, request, currentUser.ownerId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
