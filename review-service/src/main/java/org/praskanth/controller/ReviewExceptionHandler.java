package org.praskanth.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Upload-specific errors that common-library's GlobalExceptionHandler does not know about (they would otherwise
 * surface as a 500). Same {success:false, error:{...}} envelope.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class ReviewExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "The upload is too large. Each picture can be up to 5 MB and the whole review up to 30 MB.", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> missingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Missing request part: " + ex.getRequestPartName(), request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> multipart(MultipartException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The upload could not be read. Send multipart/form-data.", request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message, HttpServletRequest request) {
        log.warn("{} {} -> {} {}: {}", request.getMethod(), request.getRequestURI(), status.value(), code, message);
        ErrorResponse error = ErrorResponse.builder().code(code).message(message).path(request.getRequestURI()).build();
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }
}
