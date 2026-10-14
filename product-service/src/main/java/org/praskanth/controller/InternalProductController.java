package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.service.interfc.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. NOT reachable by clients: the API gateway rejects every /internal/** path.
 * category-service uses it to refuse deleting a category that still holds products.
 */
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countByCategory(@RequestParam String categoryId) {
        return ResponseEntity.ok(ApiResponse.success(productService.countByCategory(categoryId)));
    }
}
