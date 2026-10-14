package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CategoryNodeResponse;
import org.praskanth.dto.CategoryRequest;
import org.praskanth.dto.CategoryResponse;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reads are public (GET /api/v1/category/** is open in SecurityConfig). Anything that needs a login or a role
 * says so with @PreAuthorize on the method.
 */
@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // ---- writes -----------------------------------------------------------------------------------------

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request,
                                                                @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", categoryService.create(request, user)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable String id,
                                                                @Valid @RequestBody CategoryRequest request,
                                                                @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Category updated", categoryService.update(id, request, user)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id, @AuthenticationPrincipal GatewayUser user) {
        categoryService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    // ---- reads: storefront (public) ----------------------------------------------------------------------

    /** The whole active tree, nested and ordered. One cheap, cached call renders the entire menu. */
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryNodeResponse>>> tree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getTree()));
    }

    /** Pretty URLs: GET /api/v1/category/by-path?path=mens/shirts/van-heusen */
    @GetMapping("/by-path")
    public ResponseEntity<ApiResponse<CategoryResponse>> byPath(@RequestParam String path) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getByPath(path)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    /** Flat lists of ACTIVE categories: ?parentId=  or  ?level=  or  ?topLevelOnly=true  (default: all). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> list(
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false, defaultValue = "false") boolean topLevelOnly) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.list(parentId, level, topLevelOnly)));
    }

    // ---- reads: management (login + role required) ------------------------------------------------------------

    /** Everything, inactive included, with createdBy: feeds the admin / seller category screens. */
    @GetMapping("/manage")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> manage() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.listForManagement()));
    }

    /** The categories I created. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> mine(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.listCreatedBy(user.id())));
    }
}