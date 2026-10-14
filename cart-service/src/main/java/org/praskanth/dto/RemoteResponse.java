package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Minimal reader for the {success, data} envelope other services answer with. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteResponse<T> {
    private boolean success;
    private T data;
}
