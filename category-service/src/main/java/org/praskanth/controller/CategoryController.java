package org.praskanth.controller;

import org.praskanth.config.UserApiCall;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CategoryRequest;
import org.praskanth.dto.CategoryResponse;
import org.praskanth.dto.UserResponse;
import org.praskanth.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final UserApiCall userApiCall;

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request,
                                                                @RequestHeader("Authorization") String jwt) {
        ApiResponse<UserResponse> apiResponse = userApiCall.getUserProfile(jwt);
        UserResponse user = apiResponse.getData();

        String userRole = user.getRole() != null && !user.getRole().isEmpty()
                ? user.getRole().iterator().next()
                : "UNKNOWN";


        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", categoryService.create(request, userRole)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAll(
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false, defaultValue = "false") boolean topLevelOnly) {

        List<CategoryResponse> result;
        if (parentId != null) {
            result = categoryService.getChildren(parentId);
        } else if (topLevelOnly) {
            result = categoryService.getTopLevel();
        } else {
            result = categoryService.getAll();
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Category updated", categoryService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }
}
