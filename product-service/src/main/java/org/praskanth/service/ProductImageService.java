package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.config.ProductImageProperties;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.model.OptionGallery;
import org.praskanth.model.Product;
import org.praskanth.model.ProductVariant;
import org.praskanth.repository.ProductRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.storage.ImageProcessor;
import org.praskanth.storage.ImageStorage;
import org.praskanth.storage.ImageValidator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Product pictures: upload, remove, reorder, clean up.
 *
 * MongoDB stores only lists of URLs; the first one of a list is its main picture. A picture uploaded here is stored as
 * "/api/v1/product/images/&lt;uuid&gt;.jpg" (served by this service, public). A seller may also add an external https link.
 *
 * Two kinds of lists:
 * <ul>
 *   <li>the product's COMMON pictures ({@code value == null}), changed with MongoDB's atomic push / pull, so two uploads
 *       at the same moment cannot overwrite each other;</li>
 *   <li>a GALLERY per value of the first option, e.g. value "Blue" = the pictures of every blue variant. Changed by
 *       loading and saving the product (a seller edits one gallery at a time).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageService {

    /** Relative on purpose: it works behind the gateway, the Vite dev proxy and any future domain without migration. */
    public static final String URL_PREFIX = "/api/v1/product/images/";

    private static final Pattern EXTERNAL_URL = Pattern.compile("^https?://\\S{4,240}$");

    private final MongoTemplate mongo;
    private final ProductRepository productRepo;
    private final ImageStorage storage;
    private final ImageValidator validator;
    private final ProductImageProperties props;

    /** "uuid.jpg" -> "uuid_t.jpg" (the small copy). */
    public static String thumbKey(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(0, dot) + "_t" + key.substring(dot);
    }

    public static boolean isStoredHere(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    // ------------------------------------------------------------------------------------------------ add

    /** @param value null = the product's common pictures; otherwise the first-option value whose gallery is changed */
    /**
     * Decoding and resizing pictures is CPU- and memory-heavy. At most 3 uploads are processed at the same time per
     * instance (resilience4j.bulkhead.instances.imageProcessing); a 4th waits up to 2 s, then gets HTTP 503 so the
     * seller can retry, instead of the whole service slowing down for everyone.
     */
    @Bulkhead(name = "imageProcessing")
    public List<String> addFiles(String productId, String value, List<MultipartFile> files, GatewayUser actor) {
        Product product = loadOwned(productId, actor);
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Choose at least one picture.");
        }
        int slots = props.getMaxCount() - listOf(product, value).size();
        List<ImageValidator.DetectedImage> kinds = validator.validateAll(files, slots);

        List<String> storedKeys = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                ImageProcessor.Result processed = ImageProcessor.process(files.get(i).getBytes(), kinds.get(i).extension(),
                        props.getMainMaxPx(), props.getThumbMaxPx(), props.getMaxPixels());
                String key = UUID.randomUUID() + "." + processed.extension();
                storage.put(key, processed.main());
                storedKeys.add(key);
                if (processed.thumb() != null) {
                    String thumb = thumbKey(key);
                    storage.put(thumb, processed.thumb());
                    storedKeys.add(thumb);
                }
                urls.add(URL_PREFIX + key);
            }
            attach(product, value, urls);
        } catch (ImageProcessor.RejectedImageException e) {
            storedKeys.forEach(storage::delete);
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            storedKeys.forEach(storage::delete);
            throw new UncheckedIOException("Could not read the uploaded picture", e);
        } catch (RuntimeException e) {
            storedKeys.forEach(storage::delete); // nothing is left behind when the request fails half way
            throw e;
        }
        return currentUrls(productId, value);
    }

    public List<String> addExternalUrl(String productId, String value, String url, GatewayUser actor) {
        Product product = loadOwned(productId, actor);
        String clean = url == null ? "" : url.trim();
        if (!EXTERNAL_URL.matcher(clean).matches()) {
            throw new BadRequestException("Enter a full picture link starting with https://");
        }
        List<String> current = listOf(product, value);
        if (current.size() >= props.getMaxCount()) {
            throw new BadRequestException("At most " + props.getMaxCount() + " pictures are allowed here.");
        }
        if (current.contains(clean)) {
            throw new BadRequestException("That picture is already there.");
        }
        attach(product, value, List.of(clean));
        return currentUrls(productId, value);
    }

    // ---------------------------------------------------------------------------------- remove / reorder

    public List<String> remove(String productId, String value, String url, GatewayUser actor) {
        Product product = loadOwned(productId, actor);
        if (url == null || !listOf(product, value).contains(url)) {
            throw new NotFoundException("That picture is not there.");
        }
        if (value == null) {
            mongo.updateFirst(byId(productId), new Update().pull("imageUrl", url), Product.class);
        } else {
            gallery(product, value, false).remove(url);
            productRepo.save(product);
        }
        deleteFiles(url);
        return currentUrls(productId, value);
    }

    /** The new order must contain exactly the pictures that are there now (no additions, no removals). */
    public List<String> reorder(String productId, String value, List<String> wanted, GatewayUser actor) {
        Product product = loadOwned(productId, actor);
        List<String> current = listOf(product, value);
        if (wanted.size() != current.size() || !new HashSet<>(wanted).equals(new HashSet<>(current))) {
            throw new BadRequestException("The list must contain exactly the current pictures. Reload and try again.");
        }
        if (value == null) {
            mongo.updateFirst(byId(productId), new Update().set("imageUrl", wanted), Product.class);
        } else {
            List<String> target = gallery(product, value, false);
            target.clear();
            target.addAll(wanted);
            productRepo.save(product);
        }
        return currentUrls(productId, value);
    }

    /** Called when a product is deleted: removes all its files too (common pictures and every gallery). */
    public void deleteAllFor(Product product) {
        nonNull(product.getImageUrl()).forEach(this::deleteFiles);
        if (product.getGalleries() != null) {
            product.getGalleries().forEach(g -> nonNull(g.getImageUrl()).forEach(this::deleteFiles));
        }
    }

    // ------------------------------------------------------------------------------------------ helpers

    private void attach(Product product, String value, List<String> urls) {
        if (value == null) {
            mongo.updateFirst(byId(product.getId()), new Update().push("imageUrl").each(urls.toArray()), Product.class);
        } else {
            gallery(product, value, true).addAll(urls);
            productRepo.save(product);
        }
    }

    /** The picture list of a gallery (created on demand), after checking that the value belongs to the first option. */
    private List<String> gallery(Product product, String value, boolean create) {
        String match = firstOptionValue(product, value);
        if (product.getGalleries() == null) {
            product.setGalleries(new ArrayList<>());
        } else {
            product.setGalleries(new ArrayList<>(product.getGalleries()));
        }
        for (OptionGallery g : product.getGalleries()) {
            if (g.getValue().equalsIgnoreCase(match)) {
                if (g.getImageUrl() == null || !(g.getImageUrl() instanceof ArrayList)) {
                    g.setImageUrl(g.getImageUrl() == null ? new ArrayList<>() : new ArrayList<>(g.getImageUrl()));
                }
                return g.getImageUrl();
            }
        }
        OptionGallery fresh = OptionGallery.builder().value(match).imageUrl(new ArrayList<>()).build();
        if (create) {
            product.getGalleries().add(fresh);
        }
        return fresh.getImageUrl();
    }

    /** Pictures per option value only make sense for a product with variants, and only for a value that exists. */
    private static String firstOptionValue(Product product, String value) {
        if (product.getOptionNames() == null || product.getOptionNames().isEmpty() || product.getVariants() == null) {
            throw new BadRequestException("This product has no options, so pictures belong to the product itself.");
        }
        String first = product.getOptionNames().get(0);
        for (ProductVariant v : product.getVariants()) {
            String candidate = v.getOptions() == null ? null : v.getOptions().get(first);
            if (candidate != null && candidate.equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        throw new BadRequestException("'" + value + "' is not a value of '" + first + "' on this product.");
    }

    private List<String> listOf(Product product, String value) {
        if (value == null) {
            return nonNull(product.getImageUrl());
        }
        String match = firstOptionValue(product, value);
        if (product.getGalleries() != null) {
            for (OptionGallery g : product.getGalleries()) {
                if (g.getValue().equalsIgnoreCase(match)) {
                    return nonNull(g.getImageUrl());
                }
            }
        }
        return List.of();
    }

    private List<String> currentUrls(String productId, String value) {
        Product fresh = mongo.findById(productId, Product.class);
        return fresh == null ? List.of() : listOf(fresh, value);
    }

    private void deleteFiles(String url) {
        if (!isStoredHere(url)) {
            return; // an external link: nothing of ours to delete
        }
        String key = url.substring(URL_PREFIX.length());
        storage.delete(key);
        storage.delete(thumbKey(key));
    }

    private Product loadOwned(String productId, GatewayUser actor) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("We couldn't find that product. It may have been removed."));
        boolean owner = product.getSellerId() != null && product.getSellerId().equals(actor.id());
        if (!actor.isAdmin() && !owner) {
            throw new ForbiddenOperationException("You can only change the pictures of your own products");
        }
        return product;
    }

    private static Query byId(String productId) {
        return Query.query(Criteria.where("id").is(productId));
    }

    private static List<String> nonNull(List<String> list) {
        return list == null ? List.of() : list;
    }
}
