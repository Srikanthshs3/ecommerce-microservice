package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ImageDtos;
import org.praskanth.exception.NotFoundException;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.ProductImageService;
import org.praskanth.storage.ImageStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Product pictures. Writes: seller (own products) or admin. Reading a picture is public (GET /api/v1/product/** is
 * open at the gateway and here), because an &lt;img&gt; tag cannot send a login token.
 * Every write returns the full, current list of picture URLs of the list it changed so the page can simply show it.
 * Optional query parameter {@code value}: for a product with variants, the first-option value (e.g. "Blue") whose
 * gallery is changed; left out = the product's common pictures.
 */
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService images;
    private final ImageStorage storage;

    /** multipart/form-data, one or more file parts named "images". */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<String>>> upload(@PathVariable String id,
                                                            @RequestParam(required = false) String value,
                                                            @RequestPart("images") List<MultipartFile> files,
                                                            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pictures added", images.addFiles(id, value, files, user)));
    }

    @PostMapping("/{id}/images/url")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<String>>> addUrl(@PathVariable String id,
                                                            @RequestParam(required = false) String value,
                                                            @Valid @RequestBody ImageDtos.ImageUrlRequest request,
                                                            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Picture added", images.addExternalUrl(id, value, request.url(), user)));
    }

    /** Body: {"imageUrl": [...all current urls in the new order...]}. The first one becomes the main picture. */
    @PutMapping("/{id}/images/order")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<String>>> reorder(@PathVariable String id,
                                                             @RequestParam(required = false) String value,
                                                             @Valid @RequestBody ImageDtos.ImageOrderRequest request,
                                                             @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Order saved", images.reorder(id, value, request.imageUrl(), user)));
    }

    /** DELETE /api/v1/product/{id}/images?url=... (the url exactly as returned in the product's imageUrl list). */
    @DeleteMapping("/{id}/images")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<String>>> remove(@PathVariable String id, @RequestParam(required = false) String value,
                                                            @RequestParam String url,
                                                            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Picture removed", images.remove(id, value, url, user)));
    }

    /**
     * Streams one stored picture. size=thumb gives the small copy (falls back to the full one when none was needed).
     * Names are random UUIDs and never change, so browsers and CDNs may cache them for a year.
     */
    @GetMapping("/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String fileName,
                                          @RequestParam(defaultValue = "full") String size) {
        Resource resource = null;
        if ("thumb".equals(size)) {
            try {
                resource = storage.get(ProductImageService.thumbKey(fileName));
            } catch (NotFoundException ignored) {
                // no small copy was made (the original was already small): serve the full one
            }
        }
        if (resource == null) {
            resource = storage.get(fileName);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(mediaTypeOf(fileName))
                .body(resource);
    }

    private static MediaType mediaTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
