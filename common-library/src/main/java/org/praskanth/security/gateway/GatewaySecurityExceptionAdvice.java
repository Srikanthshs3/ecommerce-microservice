package org.praskanth.security.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @PreAuthorize} failures are thrown from INSIDE the controller invocation, so they reach MVC exception
 * handling - where common-library's catch-all {@code handleGeneric(Exception)} used to turn them into HTTP 500.
 * This advice (higher precedence) maps them to the correct 403 / 401 instead.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
public class GatewaySecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
        return anonymous
                ? build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", request)
                : build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", request);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder().code(code).message(message).path(request.getRequestURI()).build();
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }
}
