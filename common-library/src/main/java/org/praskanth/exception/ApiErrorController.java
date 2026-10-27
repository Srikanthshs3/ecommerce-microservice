package org.praskanth.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ErrorResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The last safety net. Errors raised OUTSIDE a controller (a servlet filter, the container itself, a request that never
 * matched any handler) are forwarded to /error by the servlet container, where Spring Boot's BasicErrorController would
 * answer with its own JSON ({"timestamp","status","error","path"}) or even an HTML page. Declaring this controller
 * replaces it, so those failures leave the platform in the same envelope as everything else.
 */
@RestController
@Slf4j
public class ApiErrorController implements ErrorController {

    @RequestMapping(value = "${server.error.path:${error.path:/error}}", produces = MediaType.ALL_VALUE)
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int value = raw instanceof Integer i ? i : 500;
        HttpStatus status = HttpStatus.resolve(value) != null ? HttpStatus.resolve(value) : HttpStatus.INTERNAL_SERVER_ERROR;
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = original == null ? request.getRequestURI() : original.toString();

        String reference = null;
        String message = ErrorMessages.forStatus(status.value());
        if (status.is5xxServerError()) {
            reference = ErrorMessages.newReference();
            Object cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("{} -> {} outside any controller [reference {}]", path, status.value(), reference,
                    cause instanceof Throwable t ? t : null);
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                message = ErrorMessages.withReference(message, reference);
            }
        } else {
            log.warn("{} -> {} outside any controller", path, status.value());
        }
        ErrorResponse error = ErrorResponse.builder()
                .code(status == HttpStatus.INTERNAL_SERVER_ERROR ? "INTERNAL_ERROR" : status.name())
                .message(message)
                .path(path)
                .reference(reference)
                .build();
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.failure(error));
    }
}
