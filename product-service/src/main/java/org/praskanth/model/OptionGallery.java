package org.praskanth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** The pictures shared by all variants whose FIRST option has this value (e.g. every "Blue" variant, any size). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionGallery {

    private String value;

    @Builder.Default
    private List<String> imageUrl = new ArrayList<>();
}
