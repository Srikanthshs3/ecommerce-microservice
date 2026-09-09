package org.praskanth.controller;

import org.praskanth.client.CategoryApiClient;
import org.praskanth.config.UserApiCall;
import org.praskanth.dto.*;
import org.praskanth.exception.BadRequestException;
import org.praskanth.service.interfc.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final UserApiCall userApiCall;
    private final CategoryApiClient categoryApiClient;

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@RequestBody ProductRequest request,
            @RequestHeader("Authorization") String jwt
    ) throws Exception {

        ApiResponse<UserResponse> apiResponse = userApiCall.getUserProfile(jwt);
        UserResponse user = apiResponse.getData();
        System.out.println(user.getEmail());

        String userRole = user.getRole() != null && !user.getRole().isEmpty()
                ? user.getRole().iterator().next()
                : "UNKNOWN";
        request.setSellerId(user.getId());
        request.setKycStatus(user.getKycStatus());


        fetchCategoryId(request);

        ProductResponse response = productService.createProduct(request, userRole);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Product created",response));
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id, @RequestBody ProductRequest request
    ) throws IOException {

        fetchCategoryId(request);

        return ResponseEntity.ok(ApiResponse.success("Product updated",productService.updateProduct(id, request)));
    }

    private void fetchCategoryId(@RequestBody ProductRequest request) {
        try {
            ApiResponse<CategoryResponse> categoryResponse = categoryApiClient.getById(request.getCategoryId());
            if (categoryResponse == null || categoryResponse.getData() == null) {
                throw new BadRequestException("Category ID does not exist");
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new BadRequestException("Invalid category ID provided: "+ request.getCategoryId());
        }
    }

    @DeleteMapping("/delete/{productId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String productId){
        productService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted", null));
    }

    @GetMapping("/get-all")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String name) {

        List<ProductResponse> result;
        if (categoryId != null) {
            result = productService.getByCategory(categoryId);
        } else if (name != null) {
            result = productService.search(name);
        } else {
            result = productService.getAllProduct();
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }


    @GetMapping("/getProductById/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id){
        return ResponseEntity.ok(ApiResponse.success(productService.getProductById(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchForProduct(
            @RequestParam String name){
        List<ProductResponse> result = productService.search(name);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
