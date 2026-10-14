package org.praskanth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** JSON part "review" of the multipart create request. Pictures travel in the "images" parts. */
@Data
public class CreateReviewRequest {

    @NotBlank
    @Size(max = 64)
    private String productId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 120)
    private String title;

    @Size(max = 2000)
    private String comment;
}
