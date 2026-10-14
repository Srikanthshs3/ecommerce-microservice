package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** The few fields of order-service's OrderResponse needed to detect a purchase. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteOrder {
    private Long id;
    private String status;
    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String productId;
    }
}
