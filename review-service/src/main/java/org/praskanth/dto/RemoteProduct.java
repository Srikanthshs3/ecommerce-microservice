package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** The few fields of product-service's ProductResponse that reviews need. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteProduct {
    private String id;
    private String name;
    private Long sellerId;
}
