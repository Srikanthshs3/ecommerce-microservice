package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.ProductRequest;
import org.praskanth.dto.ProductResponse;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.kafka.ProductEventProducer;
import org.praskanth.model.Product;
import org.praskanth.repository.ProductRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.interfc.ProductService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepo;
    private final MongoTemplate mongoTemplate;
    private final ProductEventProducer productEventProducer;

    @Override
    public ProductResponse createProduct(ProductRequest request, String role, List<String> categoryPath) {
        if (!"ADMIN".equals(role) && !"SELLER".equals(role)) {
            throw new BadRequestException("Only Admins and Sellers are allowed to create products");
        }
        if ("SELLER".equals(role) && !"ACTIVE".equals(request.getKycStatus())) {
            throw new BadRequestException("Your seller account must be ACTIVE (KYC approved) to create products. Current status: "
                    + request.getKycStatus());
        }
        validatePrices(request);

        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .categoryPath(categoryPath)
                .sellerId(request.getSellerId())
                .mrpPrice(request.getMrpPrice())
                .sellingPrice(request.getSellingPrice())
                .discountPrice(calculateDiscountPercentage(request.getMrpPrice(), request.getSellingPrice()))
                .color(request.getColor())
                .imageUrl(request.getImageUrl())
                .stock(request.getStock())
                .createdAt(Instant.now())
                .build();

        Product saved = productRepo.save(product);
        // inventory-service creates the stock row (starting at request.stock) when it consumes this event
        productEventProducer.publishProductCreated(saved);
        return toProductResponse(saved);
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request, List<String> categoryPath, GatewayUser actor) {
        Product existing = findOrThrow(id);
        assertOwnerOrAdmin(existing, actor);
        validatePrices(request);

        existing.setName(request.getName().trim());
        existing.setCategoryId(request.getCategoryId());
        existing.setCategoryPath(categoryPath);
        existing.setDescription(request.getDescription());
        existing.setImageUrl(request.getImageUrl());
        existing.setSellingPrice(request.getSellingPrice());
        existing.setMrpPrice(request.getMrpPrice());
        existing.setDiscountPrice(calculateDiscountPercentage(request.getMrpPrice(), request.getSellingPrice()));
        existing.setColor(request.getColor());
        // NOTE: "stock" is deliberately NOT applied here. Live stock belongs to inventory-service
        // (it is reserved/released by orders); change it with POST /api/v1/inventory/{productId}/restock.
        return toProductResponse(productRepo.save(existing));
    }

    @Override
    public void deleteProduct(String productId, GatewayUser actor) {
        Product product = findOrThrow(productId);
        assertOwnerOrAdmin(product, actor);
        productRepo.delete(product);
    }

    @Override
    public ProductResponse getProductById(String id) {
        return toProductResponse(findOrThrow(id));
    }

    @Override
    public List<ProductResponse> find(String categoryId, String name, int limit) {
        Query query = buildQuery(categoryId, name)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(query, Product.class).stream().map(this::toProductResponse).toList();
    }

    @Override
    public List<ProductResponse> findBySeller(Long sellerId) {
        Query query = new Query(Criteria.where("sellerId").is(sellerId)).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, Product.class).stream().map(this::toProductResponse).toList();
    }

    @Override
    public long countByCategory(String categoryId) {
        return mongoTemplate.count(buildQuery(categoryId, null), Product.class);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    /**
     * categoryPath = id matches the category and everything below it (a product's path contains all its ancestors).
     * categoryId = id keeps products created before categoryPath existed findable.
     * The name is quoted, so characters like ( or * typed by a user are searched literally, not as a regex.
     */
    private Query buildQuery(String categoryId, String name) {
        List<Criteria> filters = new ArrayList<>();
        if (StringUtils.hasText(categoryId)) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("categoryPath").is(categoryId),
                    Criteria.where("categoryId").is(categoryId)));
        }
        if (StringUtils.hasText(name)) {
            filters.add(Criteria.where("name").regex(Pattern.quote(name.trim()), "i"));
        }
        Query query = new Query();
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }
        return query;
    }

    private void assertOwnerOrAdmin(Product product, GatewayUser actor) {
        if (actor.isAdmin()) {
            return;
        }
        if (product.getSellerId() != null && product.getSellerId().equals(actor.id())) {
            return;
        }
        throw new ForbiddenOperationException("You can only change your own products");
    }

    private void validatePrices(ProductRequest request) {
        if (request.getSellingPrice().compareTo(request.getMrpPrice()) > 0) {
            throw new BadRequestException("Selling price cannot be higher than the MRP");
        }
    }

    private Product findOrThrow(String id) {
        return productRepo.findById(id).orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    private BigDecimal calculateDiscountPercentage(BigDecimal mrpPrice, BigDecimal sellingPrice) {
        if (mrpPrice == null || sellingPrice == null || mrpPrice.compareTo(sellingPrice) <= 0) {
            return BigDecimal.ZERO;
        }
        return mrpPrice.subtract(sellingPrice)
                .divide(mrpPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ProductResponse toProductResponse(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategoryId(),
                product.getCategoryPath(),
                product.getSellerId(),
                product.getDescription(),
                product.getMrpPrice(),
                product.getSellingPrice(),
                product.getDiscountPrice(),
                product.getImageUrl(),
                product.getColor(),
                product.getCreatedAt());
    }
}
