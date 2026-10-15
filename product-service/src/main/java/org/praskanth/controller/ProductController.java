package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.client.CategoryApiClient;
import org.praskanth.config.UserApiCall;
import org.praskanth.dto.*;
import org.praskanth.exception.BadRequestException;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.interfc.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;

/** Reads are public (GET /api/v1/product/** is open in SecurityConfig); anything else says so with @PreAuthorize. */
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final ProductService productService;
    private final UserApiCall userApiCall;
    private final CategoryApiClient categoryApiClient;

    // ---- writes ----------------------------------------------------------------------------------------

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request,
                                                                      @AuthenticationPrincipal GatewayUser user) {
        String userRole = user.primaryRole();
        request.setSellerId(user.id());
        if ("SELLER".equals(userRole)) {
            // KYC status changes at runtime (admin approval) so it is deliberately NOT carried in the JWT, which can be
            // up to 15 minutes old: ask the owning service for the current value. Admins skip the KYC rule entirely.
            SellerProfileResponse seller = userApiCall.getMySellerProfile().getData();
            request.setKycStatus(seller != null ? seller.kycStatus() : null);
        }
        List<String> categoryPath = resolveCategoryPath(request.getCategoryId());
        ProductResponse response = productService.createProduct(request, userRole, categoryPath);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Product created", response));
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable String id,
                                                                      @Valid @RequestBody ProductRequest request,
                                                                      @AuthenticationPrincipal GatewayUser user) {
        List<String> categoryPath = resolveCategoryPath(request.getCategoryId());
        return ResponseEntity.ok(ApiResponse.success("Product updated",
                productService.updateProduct(id, request, categoryPath, user)));
    }

    @DeleteMapping("/delete/{productId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String productId,
                                                           @AuthenticationPrincipal GatewayUser user) {
        productService.deleteProduct(productId, user);
        return ResponseEntity.ok(ApiResponse.success("Product deleted", null));
    }

    // ---- reads (public) --------------------------------------------------------------------------------

    /**
     * Browse. categoryId matches that category AND everything below it (pick "Men's" and get shirts, jeans, brands...).
     * Combine with name for a search inside a category. Newest first, at most `limit` (default 100, max 200).
     */
    @GetMapping("/get-all")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return ResponseEntity.ok(ApiResponse.success(productService.find(categoryId, name, safeLimit)));
    }

    @GetMapping("/getProductById/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProductById(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchForProduct(@RequestParam String name) {
        return ResponseEntity.ok(ApiResponse.success(productService.find(null, name, DEFAULT_LIMIT)));
    }

    // ---- reads (login + role) ----------------------------------------------------------------------------

    /** The calling seller's own products (an admin sees the products they themselves created). */
    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> myProducts(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(productService.findBySeller(user.id())));
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    /**
     * Products live in the most specific category only (a brand/type): "Men's" itself holds no products, but is found
     * through the path stored on every product below it. Returns root..category ids to store on the product.
     */
    private List<String> resolveCategoryPath(String categoryId) {
        CategoryResponse category;
        try {
            ApiResponse<CategoryResponse> response = categoryApiClient.getById(categoryId);
            category = response == null ? null : response.getData();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BadRequestException("Category does not exist: " + categoryId);
            // any other failure (category-service down, timeout, 5xx) is NOT hidden as a "bad category":
            // it propagates and the global handler answers 503 / 502
        }
        if (category == null) {
            throw new BadRequestException("Category does not exist: " + categoryId);
        }
        if (!Boolean.TRUE.equals(category.getSelectable())) {
            throw new BadRequestException("Products can only be listed in an active, most specific category (a brand or type). '"
                    + category.getName() + "' is not one: choose one of its sub-categories.");
        }
        List<String> path = new ArrayList<>(category.getAncestorIds() == null ? List.of() : category.getAncestorIds());
        path.add(category.getId());
        return path;
    }
}
