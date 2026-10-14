package org.praskanth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/** JSON part "review" of the multipart update request. New pictures travel in the "images" parts. */
@Data
public class UpdateReviewRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 120)
    private String title;

    @Size(max = 2000)
    private String comment;

    /** ids of existing pictures to delete */
    private List<Long> removeImageIds;
}
