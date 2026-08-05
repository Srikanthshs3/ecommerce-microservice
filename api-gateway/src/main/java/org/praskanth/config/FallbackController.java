package org.praskanth.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "code", "SERVICE_UNAVAILABLE",
                        "message", "The requested service is temporarily unavailable. Please try again shortly.",
                        "timestamp", Instant.now().toString()
                )
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
