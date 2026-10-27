package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private String path;
    private List<String> details;

    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Short code shown to the customer for server faults; the same code is in the log next to the stack trace. */
    private String reference;

    /** Correlates this response with the logs/traces of the request (empty when tracing is off). */
    @Builder.Default
    private String traceId = MDC.get("traceId");
}
