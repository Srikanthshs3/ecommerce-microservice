package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request bodies of the product-picture endpoints. */
public final class ImageDtos {

    private ImageDtos() {
    }

    /** Add a picture that lives somewhere else (an https link). */
    public record ImageUrlRequest(@NotBlank @Size(max = 250) String url) {
    }

    /** The complete list of this product's picture URLs in the wanted order (first = main picture). */
    public record ImageOrderRequest(@NotNull List<String> imageUrl) {
    }
}
