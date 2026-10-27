package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.ProductRequest;
import org.praskanth.dto.ProductResponse;
import org.praskanth.dto.VariantDtos;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.kafka.ProductEventProducer;
import org.praskanth.model.OptionGallery;
import org.praskanth.model.Product;
import org.praskanth.model.ProductVariant;
import org.praskanth.repository.ProductRepository;
import org.praskanth.service.ProductImageService;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.interfc.ProductService;
import org.praskanth.variant.VariantRules;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepo;
    private final MongoTemplate mongoTemplate;
    private final ProductEventProducer productEventProducer;
    private final ProductImageService productImageService;

    @Override
    public ProductResponse createProduct(ProductRequest request, String role, List<String> categoryPath) {
        if (!"ADMIN".equals(role) && !"SELLER".equals(role)) {
            throw new BadRequestException("Only Admins and Sellers are allowed to create products");
        }
        if ("SELLER".equals(role) && !"ACTIVE".equals(request.getKycStatus())) {
            throw new BadRequestException("Your seller account must be ACTIVE (KYC approved) to create products. Current status: "
                    + request.getKycStatus());
        }

        List<String> optionNames = cleanNames(request.getOptionNames());
        boolean withVariants = !optionNames.isEmpty() || hasVariantInput(request);

        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .categoryPath(categoryPath)
                .sellerId(request.getSellerId())
                .color(request.getColor())
                .imageUrl(externalImageLinks(request.getImageUrl()))
                .createdAt(Instant.now())
                .build();

        VariantRules.Merged merged = null;
        if (withVariants) {
            merged = mergeVariants(optionNames, List.of(), request);
            applyVariants(product, optionNames, merged);
            product.setStock(merged.newStock().values().stream().mapToInt(Integer::intValue).sum());
        } else {
            requireSimpleFields(request, true);
            validatePrices(request);
            product.setMrpPrice(request.getMrpPrice());
            product.setSellingPrice(request.getSellingPrice());
            product.setDiscountPrice(calculateDiscountPercentage(request.getMrpPrice(), request.getSellingPrice()));
            product.setStock(request.getStock());
        }

        Product saved = productRepo.save(product);
        // inventory-service creates the stock row(s) when it consumes these events
        if (merged == null) {
            productEventProducer.publishProductCreated(saved);
        } else {
            publishNewVariantStock(saved, merged);
        }
        return toProductResponse(saved);
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request, List<String> categoryPath, GatewayUser actor) {
        Product existing = findOrThrow(id);
        assertOwnerOrAdmin(existing, actor);

        existing.setName(request.getName().trim());
        existing.setCategoryId(request.getCategoryId());
        existing.setCategoryPath(categoryPath);
        existing.setDescription(request.getDescription());
        existing.setColor(request.getColor());
        // pictures are NOT touched here: they are managed by the picture endpoints (POST/DELETE /{id}/images ...),
        // so saving the text fields of a product can never wipe its photos.
        // "stock" is deliberately NOT applied either: live stock belongs to inventory-service
        // (it is reserved/released by orders); change it with POST /api/v1/inventory/{stockId}/restock.

        boolean hasVariants = existing.getVariants() != null && !existing.getVariants().isEmpty();
        VariantRules.Merged merged = null;
        if (request.getOptionNames() != null) {
            List<String> names = cleanNames(request.getOptionNames());
            if (names.isEmpty()) {
                if (hasVariants) {
                    throw new BadRequestException("A product that has variants cannot go back to having none. Switch the unwanted variants off instead, or delete the product.");
                }
            } else {
                if (hasVariants && !names.equals(existing.getOptionNames())) {
                    throw new BadRequestException("The option names of a product with variants cannot be changed (" + String.join(", ", existing.getOptionNames()) + ").");
                }
                merged = mergeVariants(names, toRows(existing.getVariants()), request);
                applyVariants(existing, names, merged);
                hasVariants = true;
            }
        }
        if (!hasVariants) {
            requireSimpleFields(request, false);
            validatePrices(request);
            existing.setSellingPrice(request.getSellingPrice());
            existing.setMrpPrice(request.getMrpPrice());
            existing.setDiscountPrice(calculateDiscountPercentage(request.getMrpPrice(), request.getSellingPrice()));
        }
        // a product WITH variants and no optionNames in the request: prices stay derived from its variants

        Product saved = productRepo.save(existing);
        if (merged != null) {
            publishNewVariantStock(saved, merged);
        }
        return toProductResponse(saved);
    }

    @Override
    public void deleteProduct(String productId, GatewayUser actor) {
        Product product = findOrThrow(productId);
        assertOwnerOrAdmin(product, actor);
        productRepo.delete(product);
        productImageService.deleteAllFor(product); // its files go with it
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

    /** At creation only external https links are accepted; uploaded pictures arrive through the upload endpoint. */
    private List<String> externalImageLinks(List<String> links) {
        if (links == null) {
            return new ArrayList<>();
        }
        List<String> clean = new ArrayList<>();
        for (String link : links) {
            String value = link == null ? "" : link.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.matches("^https?://\\S{4,240}$")) {
                throw new BadRequestException("Picture links must start with http:// or https://");
            }
            clean.add(value);
        }
        return clean;
    }

    private void validatePrices(ProductRequest request) {
        if (request.getSellingPrice().compareTo(request.getMrpPrice()) > 0) {
            throw new BadRequestException("Selling price cannot be higher than the MRP");
        }
    }

    private Product findOrThrow(String id) {
        return productRepo.findById(id).orElseThrow(() -> new NotFoundException("We couldn't find that product. It may have been removed."));
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

    // ---- variants -----------------------------------------------------------------------------------------

    private static List<String> cleanNames(List<String> raw) {
        try {
            return VariantRules.cleanOptionNames(raw);
        } catch (VariantRules.RuleViolation e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static boolean hasVariantInput(ProductRequest request) {
        return request.getVariants() != null && !request.getVariants().isEmpty();
    }

    private static VariantRules.Merged mergeVariants(List<String> optionNames, List<VariantRules.Row> stored, ProductRequest request) {
        List<VariantRules.Input> inputs = new ArrayList<>();
        if (request.getVariants() != null) {
            for (VariantDtos.VariantRequest v : request.getVariants()) {
                inputs.add(new VariantRules.Input(v.id(), v.options(), v.mrpPrice(), v.sellingPrice(), v.stock()));
            }
        }
        try {
            return VariantRules.merge(optionNames, stored, inputs, VariantRules::newId);
        } catch (VariantRules.RuleViolation e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** Stores the merged variants on the product and derives the product-level price ("From ...") from the cheapest. */
    private static void applyVariants(Product product, List<String> optionNames, VariantRules.Merged merged) {
        product.setOptionNames(optionNames);
        product.setVariants(merged.rows().stream().map(r -> ProductVariant.builder()
                .id(r.id())
                .options(new LinkedHashMap<>(r.options()))
                .mrpPrice(r.mrpPrice())
                .sellingPrice(r.sellingPrice())
                .discountPrice(r.discountPercent())
                .active(r.active())
                .build()).toList());
        VariantRules.Row cheapest = VariantRules.cheapest(merged.rows());
        product.setMrpPrice(cheapest.mrpPrice());
        product.setSellingPrice(cheapest.sellingPrice());
        product.setDiscountPrice(cheapest.discountPercent());
    }

    private static List<VariantRules.Row> toRows(List<ProductVariant> variants) {
        List<VariantRules.Row> rows = new ArrayList<>();
        if (variants != null) {
            for (ProductVariant v : variants) {
                rows.add(new VariantRules.Row(v.getId(), v.getOptions(), v.getMrpPrice(), v.getSellingPrice(), v.getDiscountPrice(), v.isActive()));
            }
        }
        return rows;
    }

    private void publishNewVariantStock(Product saved, VariantRules.Merged merged) {
        for (VariantRules.Row row : merged.rows()) {
            Integer opening = merged.newStock().get(row.id());
            if (opening != null) {
                productEventProducer.publishVariantCreated(saved, VariantRules.stockId(saved.getId(), row.id()),
                        VariantRules.label(row.options()), row.mrpPrice(), opening);
            }
        }
    }

    /** A simple product still needs its own price and (when created) its opening stock. */
    private static void requireSimpleFields(ProductRequest request, boolean creating) {
        if (request.getMrpPrice() == null || request.getSellingPrice() == null) {
            throw new BadRequestException("MRP and selling price are required (or add variants, which carry their own prices).");
        }
        if (creating && request.getStock() == null) {
            throw new BadRequestException("Opening stock is required (or add variants, which carry their own stock).");
        }
    }

    private ProductResponse toProductResponse(Product product) {
        if (product == null) {
            return null;
        }
        List<String> optionNames = product.getOptionNames() == null ? List.of() : product.getOptionNames();
        List<OptionGallery> galleries = product.getGalleries() == null ? List.of() : product.getGalleries();

        Map<String, List<String>> galleryMap = new LinkedHashMap<>();
        for (OptionGallery g : galleries) {
            galleryMap.put(g.getValue(), g.getImageUrl() == null ? List.of() : g.getImageUrl());
        }
        List<String> common = product.getImageUrl() == null ? List.of() : product.getImageUrl();

        List<VariantDtos.VariantResponse> variants = new ArrayList<>();
        if (product.getVariants() != null) {
            for (ProductVariant v : product.getVariants()) {
                if (!v.isActive()) {
                    continue; // switched off: invisible to everybody
                }
                VariantRules.Row row = new VariantRules.Row(v.getId(), v.getOptions(), v.getMrpPrice(), v.getSellingPrice(), v.getDiscountPrice(), true);
                variants.add(new VariantDtos.VariantResponse(v.getId(), v.getOptions(), VariantRules.label(v.getOptions()),
                        VariantRules.stockId(product.getId(), v.getId()), v.getMrpPrice(), v.getSellingPrice(), v.getDiscountPrice(),
                        VariantRules.resolveImages(optionNames, row, galleryMap, common)));
            }
        }

        // cards and lists show ONE picture: the common ones, else the first colour's
        List<String> pictures = common;
        if (pictures.isEmpty()) {
            for (VariantDtos.VariantResponse v : variants) {
                if (!v.imageUrl().isEmpty()) {
                    pictures = v.imageUrl();
                    break;
                }
            }
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
                pictures,
                product.getColor(),
                product.getCreatedAt(),
                optionNames,
                variants,
                galleries.stream().map(g -> new VariantDtos.GalleryResponse(g.getValue(), g.getImageUrl() == null ? List.of() : g.getImageUrl())).toList());
    }
}
