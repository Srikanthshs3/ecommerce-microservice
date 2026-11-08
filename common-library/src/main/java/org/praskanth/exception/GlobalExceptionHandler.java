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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
 *  - 4xx are the CALLER's problem: logged at WARN without a stack trace, message in everyday words.
 *  - 5xx are OUR problem: logged at ERROR with the stack trace (and a short reference). The client sees a friendly
 *    sentence plus that reference, never the exception text (SQL, class names, host names, service names).
 *  - Errors that another service answered with (RestTemplate / Feign) keep that service's own friendly sentence.
 *  - All wording lives in ErrorMessages; this class only decides status + code.
 *  - One handler for the whole platform: no service needs its own @RestControllerAdvice (uploads are handled here too).
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
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? ErrorMessages.forCode("SERVICE_UNAVAILABLE") : ex.getMessage();
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message, null, request, ex,
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
                .distinct()
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ErrorMessages.validationSummary(details), details, request, ex);
    }

    /** Constraints on @RequestParam / @PathVariable (Spring 6.1+ method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<String> details = ex.getAllErrors().stream()
                .map(e -> ErrorMessages.validationSentence("", e.getDefaultMessage()))
                .distinct()
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ErrorMessages.validationSummary(details), details, request, ex);
    }

    /** @Validated on a class / manual validator calls. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::describeViolation)
                .distinct()
                .sorted()
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ErrorMessages.validationSummary(details), details, request, ex);
    }

    /** Body missing, not JSON, wrong types, unknown enum value... */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", ErrorMessages.forCode("MALFORMED_REQUEST"), null, request, ex);
    }

    /** Required @RequestParam / @RequestHeader / @CookieValue absent. */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(ServletRequestBindingException ex, HttpServletRequest request) {
        String message = ex instanceof MissingServletRequestParameterException missing
                ? "Some required information is missing: " + ErrorMessages.humanizeField(missing.getParameterName()).toLowerCase() + "."
                : ErrorMessages.forCode("BAD_REQUEST");
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, null, request, ex);
    }

    /** e.g. ?kycStatus=FOO, /order/abc where a number is expected. */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException ex, HttpServletRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "The value for " + ErrorMessages.humanizeField(field).toLowerCase() + " isn't valid.", null, request, ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String allow = ex.getSupportedHttpMethods() == null ? null
                : ex.getSupportedHttpMethods().stream().map(HttpMethod::name).collect(Collectors.joining(", "));
        String message = ErrorMessages.forCode("METHOD_NOT_ALLOWED");
        return allow == null
                ? respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, null, request, ex)
                : respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, null, request, ex,
                        HttpHeaders.ALLOW, allow);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                ErrorMessages.forCode("UNSUPPORTED_MEDIA_TYPE"), null, request, ex);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", ErrorMessages.forCode("NOT_ACCEPTABLE"), null, request, ex);
    }

    /** Unknown URL. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ErrorMessages.forCode("NOT_FOUND"), null, request, ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode code = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        String message = !code.is4xxClientError() || ex.getReason() == null || ex.getReason().isBlank()
                ? ErrorMessages.forStatus(code.value()) : ex.getReason();
        return respond(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR,
                status != null ? status.name() : "INTERNAL_ERROR", message, null, request, ex);
    }

    // =====================================================================================================
    //  Uploads (used to be copy-pasted into product-service and review-service)
    // =====================================================================================================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "The upload is too large. Please choose smaller pictures (up to 5 MB each) and try again.", null, request, ex);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Please choose a file to upload.", null, request, ex);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "We couldn't read the uploaded file. Please try again.", null, request, ex);
    }

    // =====================================================================================================
    //  Everything else: classify, never leak internals
    // =====================================================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        DownstreamError downstream = DownstreamError.find(ex);
        if (downstream != null) {
            return handleDownstream(downstream, ex, request);
        }
        return switch (ExceptionClassifier.classify(ex)) {
            case DATA_CONFLICT -> respond(HttpStatus.CONFLICT, "DATA_CONFLICT",
                    ErrorMessages.forCode("DATA_CONFLICT"), null, request, ex);
            case CONCURRENT_UPDATE -> respond(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                    ErrorMessages.forCode("CONCURRENT_UPDATE"), null, request, ex);
            case DATABASE_UNAVAILABLE -> respond(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                    ErrorMessages.forCode("DATABASE_UNAVAILABLE"), null, request, ex, HttpHeaders.RETRY_AFTER, "5");
            case UPSTREAM_UNAVAILABLE -> respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    ErrorMessages.forCode("SERVICE_UNAVAILABLE"), null, request, ex, HttpHeaders.RETRY_AFTER, "5");
            case UPSTREAM_ERROR -> respond(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    ErrorMessages.forCode("UPSTREAM_ERROR"), null, request, ex);
            case OTHER -> respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    ErrorMessages.forCode("INTERNAL_ERROR"), null, request, ex);
        };
    }

    /**
     * Another service answered with an error. What the customer should see depends on WHO is at fault:
     *  - it said "your request is wrong" (400, 404, 409 ...): pass its friendly sentence on, with the same status;
     *  - 401 from a service-to-service call is OUR wiring, never the customer's session: 502, otherwise the browser
     *    would log the customer out for nothing;
     *  - it is down, slow, or broken (no answer, 502/503/504, 5xx): "temporarily unavailable", details stay in the log.
     */
    private ResponseEntity<ApiResponse<Void>> handleDownstream(DownstreamError d, Exception ex, HttpServletRequest request) {
        int status = d.status();
        boolean noAnswer = status <= 0;
        if (!noAnswer && status >= 400 && status < 500 && status != 401 && status != 408 && status != 429) {
            HttpStatus resolved = HttpStatus.resolve(status) != null ? HttpStatus.resolve(status) : HttpStatus.BAD_REQUEST;
            String message = d.message() != null ? d.message() : ErrorMessages.forStatus(status);
            return respond(resolved, resolved.name(), message, null, request, ex);
        }
        if (status == 429) {
            return respond(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    d.message() != null ? d.message() : ErrorMessages.forCode("TOO_MANY_REQUESTS"), null, request, ex,
                    HttpHeaders.RETRY_AFTER, "5");
        }
        if (noAnswer || status == 408 || status == 502 || status == 503 || status == 504) {
            return respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    ErrorMessages.forCode("SERVICE_UNAVAILABLE"), null, request, ex, HttpHeaders.RETRY_AFTER, "5");
        }
        return respond(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", ErrorMessages.forCode("UPSTREAM_ERROR"), null, request, ex);
    }

    // =====================================================================================================
    //  helpers
    // =====================================================================================================

    private static String describeError(ObjectError error) {
        return error instanceof FieldError fe
                ? ErrorMessages.validationSentence(fe.getField(), fe.getDefaultMessage())
                : ErrorMessages.validationSentence("", error.getDefaultMessage());
    }

    private static String describeViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        return ErrorMessages.validationSentence(path, violation.getMessage()); // "createUser.arg0.email" -> "Email ..."  
    }

    /** Logs and builds the standard response. */
    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message, List<String> details,
                                                      HttpServletRequest request, Throwable cause) {
        return respond(status, code, message, details, request, cause, null, null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message, List<String> details,
                                                      HttpServletRequest request, Throwable cause,
                                                      String headerName, String headerValue) {
        String reference = null;
        if (status.is5xxServerError()) {
            // OUR problem: full stack trace in the log, a friendly message + short reference to the client
            reference = ErrorMessages.newReference();
            log.error("{} {} -> {} {} [reference {}]", request.getMethod(), request.getRequestURI(), status.value(), code, reference, cause);
            if ("INTERNAL_ERROR".equals(code)) {
                message = ErrorMessages.withReference(message, reference);
            }
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
                .reference(reference)
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
