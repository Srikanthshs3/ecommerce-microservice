package org.praskanth.service.impl;


import org.praskanth.dto.ProductRequest;
import org.praskanth.dto.ProductResponse;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.kafka.ProductEventProducer;
import org.praskanth.model.Product;
import org.praskanth.repository.ProductRepository;
import org.praskanth.service.interfc.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepo;
    private final ProductEventProducer productEventProducer;

    @Override
    public ProductResponse createProduct(ProductRequest request, String role) throws Exception {

        if (!"ADMIN".equals(role) && !"SELLER".equals(role)) {
            throw new BadRequestException("Only Admins and Sellers are allowed to create products");
        }
        if ("SELLER".equals(role)) {
            if (request.getKycStatus() == null || !"ACTIVE".equals(request.getKycStatus())) {
                throw new BadRequestException("Your seller account must be ACTIVE to create products. Current status: " + request.getKycStatus());
            }
        }

        BigDecimal discountPrice = calculateDiscountPercentage(request.getMrpPrice(), request.getSellingPrice());

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .sellerId(request.getSellerId())
                .mrpPrice(request.getMrpPrice())
                .sellingPrice(request.getSellingPrice())
                .discountPrice(discountPrice)
                .color(request.getColor())
                .imageUrl(request.getImageUrl())
                .stock(request.getStock())
                .createdAt(Instant.now())
                .build();


        Product savedProduct = productRepo.save(product);
        productEventProducer.publishProductCreated(savedProduct);

        return toProductResponse(savedProduct);
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request) throws IOException {

        Product existingProduct = productRepo.findById(id).orElseThrow(()-> new NotFoundException("Product not found"));

        BigDecimal discountPrice = calculateDiscountPercentage(request.getMrpPrice(), request.getSellingPrice());

        existingProduct.setName(request.getName());
        existingProduct.setCategoryId(request.getCategoryId());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setImageUrl(request.getImageUrl());
        existingProduct.setSellingPrice(request.getSellingPrice());
        existingProduct.setDiscountPrice(discountPrice);
        existingProduct.setColor(request.getColor());
        existingProduct.setMrpPrice(request.getMrpPrice());

        Product updateProduct = productRepo.save(existingProduct);
        return toProductResponse(updateProduct);
    }

    @Override
    public void deleteProduct(String productId) {
        Product product = findOrThrow(productId);
        productRepo.delete(product);
    }


    @Override
    public ProductResponse getProductById(String id) {
        return toProductResponse(findOrThrow(id));
    }

    @Override
    public List<ProductResponse> getAllProduct() {

        return productRepo.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream().map(this::toProductResponse).toList();
    }

    public List<ProductResponse> getByCategory(String categoryId) {
        return productRepo.findByCategoryId(categoryId).stream().map(this::toProductResponse).toList();
    }

    public List<ProductResponse> search(String name) {
        return productRepo.findByNameContainingIgnoreCase(name).stream().map(this::toProductResponse).toList();
    }

    private Product findOrThrow(String id){
        return productRepo.findById(id).orElseThrow(() -> new NotFoundException("Product not found: "+ id));
    }

    private BigDecimal calculateDiscountPercentage(BigDecimal mrpPrice, BigDecimal sellingPrice){
        if (mrpPrice == null || mrpPrice.compareTo(sellingPrice) <= 0){
            return BigDecimal.ZERO;
        }

        BigDecimal discount = mrpPrice.subtract(sellingPrice);

        if (discount.compareTo(BigDecimal.ZERO) <= 0){
            return BigDecimal.ZERO;
        }

        return discount.divide(mrpPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private ProductResponse toProductResponse(Product product) {
        if (product == null) {
            return null;
        }

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategoryId(),
                product.getSellerId(),
                product.getDescription(),
                product.getMrpPrice(),
                product.getSellingPrice(),
                product.getCreatedAt()
        );
    }

}
