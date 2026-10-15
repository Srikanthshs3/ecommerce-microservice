package org.praskanth.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ErrorResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * THE exception handler for every servlet-based service (picked up by component scan, like CorrelationIdFilter).
 * Every failure leaves the platform in the same envelope: {@code ApiResponse{success:false, error:{code,message,path,
 * details?,timestamp,traceId}}}. The reactive api-gateway has its own filter-level errors in the same shape.
 *
 * Rules:
 *  - 4xx are the CALLER's problem: logged at WARN without a stack trace, message safe to show.
 *  - 5xx are OUR problem: logged at ERROR with the stack trace, client only ever sees a generic message.
 *  - Never leak exception messages of unexpected errors (SQL, class names, hostnames) to the client.
 *
 * Security exceptions (401/403 from @PreAuthorize) are handled by GatewaySecurityExceptionAdvice, which has higher
 * precedence and only exists where Spring Security is on the classpath.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // =====================================================================================================
    //  Application exceptions (thrown deliberately by our own code)
    // =====================================================================================================

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null, request, ex);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null, request, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), null, request, ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), null, request, ex);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), null, request, ex);
    }

    @ExceptionHandler(AuthChallengeException.class)
    public ResponseEntity<?> handleAuthChallenge(AuthChallengeException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", ex.getMessage(), null, request, ex,
                HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage(), null, request, ex,
                HttpHeaders.RETRY_AFTER, "5");
    }

    // =====================================================================================================
    //  Bad requests detected by Spring (these used to fall into the catch-all and become HTTP 500)
    // =====================================================================================================

    /** @Valid @RequestBody failed. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::describeError)
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request, ex);
    }

    /** Constraints on @RequestParam / @PathVariable (Spring 6.1+ method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<String> details = ex.getAllErrors().stream()
                .map(e -> e.getDefaultMessage() == null ? "invalid value" : e.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request, ex);
    }

    /** @Validated on a class / manual validator calls. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::describeViolation)
                .sorted()
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request, ex);
    }

    /** Body missing, not JSON, wrong types, unknown enum value... */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or malformed", null, request, ex);
    }

    /** Required @RequestParam / @RequestHeader / @CookieValue absent. */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(ServletRequestBindingException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getBody().getDetail(), null, request, ex);
    }

    /** e.g. ?kycStatus=FOO, /order/abc where a number is expected. */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException ex, HttpServletRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid value for '" + field + "'", null, request, ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String allow = ex.getSupportedHttpMethods() == null ? null
                : ex.getSupportedHttpMethods().stream().map(HttpMethod::name).collect(Collectors.joining(", "));
        String message = "HTTP method " + ex.getMethod() + " is not supported for this endpoint";
        return allow == null
                ? respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, null, request, ex)
                : respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, null, request, ex,
                        HttpHeaders.ALLOW, allow);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported content type. Use application/json", null, request, ex);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "The requested response format is not available", null, request, ex);
    }

    /** Unknown URL. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null, request, ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode code = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        String reason = ex.getReason() != null ? ex.getReason() : (status != null ? status.getReasonPhrase() : "Error");
        return respond(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR,
                status != null ? status.name() : "ERROR", code.is5xxServerError() ? "Request failed" : reason, null, request, ex);
    }

    // =====================================================================================================
    //  Everything else: classify, never leak internals
    // =====================================================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        return switch (ExceptionClassifier.classify(ex)) {
            case DATA_CONFLICT -> respond(HttpStatus.CONFLICT, "DATA_CONFLICT",
                    "The request conflicts with existing data", null, request, ex);
            case CONCURRENT_UPDATE -> respond(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                    "The resource was modified by another request. Please retry.", null, request, ex);
            case UPSTREAM_UNAVAILABLE -> respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "A required service is temporarily unavailable. Please try again shortly.", null, request, ex,
                    HttpHeaders.RETRY_AFTER, "5");
            case UPSTREAM_ERROR -> respond(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "A required service returned an unexpected response", null, request, ex);
            case OTHER -> respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "An unexpected error occurred", null, request, ex);
        };
    }

    // =====================================================================================================
    //  helpers
    // =====================================================================================================

    private static String describeError(ObjectError error) {
        String message = error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage();
        return error instanceof FieldError fe ? fe.getField() + ": " + message : message;
    }

    private static String describeViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        // "createUser.arg0.email" -> "email"
        String leaf = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return (leaf.isBlank() ? "" : leaf + ": ") + violation.getMessage();
    }

    /** Logs and builds the standard response. */
    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message, List<String> details,
                                                      HttpServletRequest request, Throwable cause) {
        return respond(status, code, message, details, request, cause, null, null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message, List<String> details,
                                                      HttpServletRequest request, Throwable cause,
                                                      String headerName, String headerValue) {
        if (status.is5xxServerError()) {
            // OUR problem: full stack trace in the log, generic message to the client
            log.error("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(), code, cause);
        } else {
            // the CALLER's problem: one line, no stack trace
            log.warn("{} {} -> {} {}: {}", request.getMethod(), request.getRequestURI(), status.value(), code,
                    details == null ? message : message + " " + details);
        }
        ErrorResponse error = ErrorResponse.builder()
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .details(details == null || details.isEmpty() ? null : details)
                .build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return builder.body(ApiResponse.failure(error));
    }

    private ResponseEntity<?> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        var body = ApiResponse.failure(ErrorResponse.builder()
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .build());
        return ResponseEntity.status(status).body(body);
    }
}
