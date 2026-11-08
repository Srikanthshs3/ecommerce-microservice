package org.praskanth.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------------------------------------------------------- application exceptions

    @Test
    @DisplayName("NotFoundException -> 404 NOT_FOUND with the exception message and request path")
    void notFound_returns404() throws Exception {
        mvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("order 7 not found"))
                .andExpect(jsonPath("$.error.path").value("/t/not-found"));
    }

    @Test
    @DisplayName("BadRequestException -> 400 BAD_REQUEST")
    void badRequest_returns400() throws Exception {
        mvc.perform(get("/t/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("bad input"));
    }

    @Test
    @DisplayName("ConflictException -> 409 CONFLICT")
    void conflict_returns409() throws Exception {
        mvc.perform(get("/t/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("duplicate email"));
    }

    @Test
    @DisplayName("UnauthorizedException -> 401 UNAUTHORIZED")
    void unauthorized_returns401() throws Exception {
        mvc.perform(get("/t/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ForbiddenOperationException -> 403 FORBIDDEN")
    void forbidden_returns403() throws Exception {
        mvc.perform(get("/t/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("not your order"));
    }

    @Test
    @DisplayName("AuthChallengeException -> its own status and machine-readable code")
    void authChallenge_usesStatusAndCodeFromException() throws Exception {
        mvc.perform(get("/t/challenge"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.error.message").value("account locked"));
    }

    @Test
    @DisplayName("TooManyRequestsException -> 429 with Retry-After header")
    void tooManyRequests_returns429WithRetryAfter() throws Exception {
        mvc.perform(get("/t/too-many"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("ServiceUnavailableException -> 503 with Retry-After 5")
    void serviceUnavailable_returns503() throws Exception {
        mvc.perform(get("/t/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }

    // ---------------------------------------------------------------- validation

    @Test
    @DisplayName("@Valid body failure -> 400 VALIDATION_ERROR with 'field: message' details")
    void methodArgumentNotValid_returnsFieldDetails() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("required")))
                .andExpect(jsonPath("$.error.details[0]").value(containsString("required")));
    }

    @Test
    @DisplayName("valid body passes through untouched")
    void validBody_returns200() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("ConstraintViolationException -> 400 with sorted leaf-property details")
    void constraintViolation_returnsSortedDetails() throws Exception {
        mvc.perform(get("/t/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value(containsString("too young")))
                .andExpect(jsonPath("$.error.details[1]").value(containsString("required")));
    }

    @Test
    @DisplayName("ConstraintViolationException without violations -> no details field")
    void constraintViolation_empty_omitsDetails() throws Exception {
        mvc.perform(get("/t/constraint-empty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    @DisplayName("malformed JSON -> 400 MALFORMED_REQUEST")
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("missing required request parameter -> 400 BAD_REQUEST naming the parameter")
    void missingParameter_returns400() throws Exception {
        mvc.perform(get("/t/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value(containsString("q")));
    }

    @Test
    @DisplayName("parameter of the wrong type -> 400 BAD_REQUEST")
    void typeMismatch_returns400() throws Exception {
        mvc.perform(get("/t/number").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value(containsString("isn't valid")));
    }

    // ---------------------------------------------------------------- protocol errors

    @Test
    @DisplayName("unsupported HTTP method -> 405 with Allow header")
    void methodNotSupported_returns405WithAllow() throws Exception {
        mvc.perform(get("/t/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.message").value(containsString("isn't available")));
    }

    @Test
    @DisplayName("unsupported content type -> 415")
    void unsupportedMediaType_returns415() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("unacceptable Accept header -> 406")
    void notAcceptable_returns406() throws Exception {
        mvc.perform(get("/t/json-only").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    @DisplayName("NoResourceFoundException -> 404 'Resource not found'")
    void noResource_returns404() throws Exception {
        mvc.perform(get("/t/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("We couldn't find what you were looking for."));
    }

    @Test
    @DisplayName("NoHandlerFoundException -> 404 'Resource not found'")
    void noHandler_returns404() throws Exception {
        mvc.perform(get("/t/no-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("We couldn't find what you were looking for."));
    }

    // ---------------------------------------------------------------- ResponseStatusException

    @Test
    @DisplayName("ResponseStatusException 4xx -> status, enum name as code and reason as message")
    void responseStatus_4xx_keepsReason() throws Exception {
        mvc.perform(get("/t/rse-4xx"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("no such thing"));
    }

    @Test
    @DisplayName("ResponseStatusException 4xx without reason -> reason phrase")
    void responseStatus_4xxWithoutReason_usesReasonPhrase() throws Exception {
        mvc.perform(get("/t/rse-no-reason"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Something in your request isn't right. Please check it and try again."));
    }

    @Test
    @DisplayName("ResponseStatusException 5xx -> reason hidden behind a generic message")
    void responseStatus_5xx_hidesReason() throws Exception {
        mvc.perform(get("/t/rse-5xx"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("BAD_GATEWAY"))
                .andExpect(jsonPath("$.error.message").value("We couldn't finish that because a connected service had a problem. Please try again shortly."))
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    @Test
    @DisplayName("ResponseStatusException with a non-standard status -> 500 ERROR")
    void responseStatus_unknownStatus_returns500() throws Exception {
        mvc.perform(get("/t/rse-unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("quote reference")));
    }

    // ---------------------------------------------------------------- generic / classified

    @Test
    @DisplayName("DataIntegrityViolationException -> 409 DATA_CONFLICT without leaking SQL")
    void dataIntegrity_returns409() throws Exception {
        mvc.perform(get("/t/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DATA_CONFLICT"))
                .andExpect(content().string(not(containsString("uk_users_email"))));
    }

    @Test
    @DisplayName("optimistic locking failure -> 409 CONCURRENT_UPDATE")
    void optimisticLock_returns409() throws Exception {
        mvc.perform(get("/t/concurrent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    @DisplayName("ResourceAccessException -> 503 with Retry-After")
    void resourceAccess_returns503() throws Exception {
        mvc.perform(get("/t/upstream-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("RestClientResponseException -> 502 UPSTREAM_ERROR")
    void restClientResponse_returns502() throws Exception {
        mvc.perform(get("/t/upstream-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_ERROR"));
    }

    @Test
    @DisplayName("unexpected exception -> 500 INTERNAL_ERROR, internals never leaked")
    void unexpected_returns500WithoutLeaking() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("Something went wrong on our side")))
                .andExpect(content().string(not(containsString("jdbc:mysql"))));
    }

    @Test
    @DisplayName("every failure body carries success=false and a timestamp")
    void failureEnvelope_hasSuccessFalseAndTimestamp() throws Exception {
        mvc.perform(get("/t/not-found"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    // ---------------------------------------------------------------- test controller

    public static class Payload {
        @NotBlank(message = "name is required")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Subject {
        @NotBlank(message = "name is required")
        private String name;
        @Min(value = 18, message = "too young")
        private int age;

        public Subject(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    @RestController
    public static class ThrowingController {

        @GetMapping("/t/not-found")
        public String notFound() {
            throw new NotFoundException("order 7 not found");
        }

        @GetMapping("/t/bad-request")
        public String badRequest() {
            throw new BadRequestException("bad input");
        }

        @GetMapping("/t/conflict")
        public String conflict() {
            throw new ConflictException("duplicate email");
        }

        @GetMapping("/t/unauthorized")
        public String unauthorized() {
            throw new UnauthorizedException("bad credentials");
        }

        @GetMapping("/t/forbidden")
        public String forbidden() {
            throw new ForbiddenOperationException("not your order");
        }

        @GetMapping("/t/challenge")
        public String challenge() {
            throw new AuthChallengeException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "account locked");
        }

        @GetMapping("/t/too-many")
        public String tooMany() {
            throw new TooManyRequestsException("slow down", 30);
        }

        @GetMapping("/t/unavailable")
        public String unavailable() {
            throw new ServiceUnavailableException("inventory down");
        }

        @PostMapping("/t/body")
        public String body(@Valid @RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/t/constraint")
        public String constraint() {
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            Set<ConstraintViolation<Subject>> violations = validator.validate(new Subject("", 5));
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/t/constraint-empty")
        public String constraintEmpty() {
            throw new ConstraintViolationException("nothing", Set.of());
        }

        @GetMapping("/t/param")
        public String param(@RequestParam("q") String q) {
            return q;
        }

        @GetMapping("/t/number")
        public String number(@RequestParam("n") int n) {
            return String.valueOf(n);
        }

        @GetMapping(value = "/t/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
        public String jsonOnly() {
            return "{}";
        }

        @GetMapping("/t/no-resource")
        public String noResource() throws Exception {
            throw new NoResourceFoundException(HttpMethod.GET, "missing.txt");
        }

        @GetMapping("/t/no-handler")
        public String noHandler() throws Exception {
            throw new NoHandlerFoundException("GET", "/missing", new HttpHeaders());
        }

        @GetMapping("/t/rse-4xx")
        public String rse4xx() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such thing");
        }

        @GetMapping("/t/rse-no-reason")
        public String rseNoReason() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        @GetMapping("/t/rse-5xx")
        public String rse5xx() {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "internal detail");
        }

        @GetMapping("/t/rse-unknown")
        public String rseUnknown() {
            throw new ResponseStatusException(HttpStatusCode.valueOf(299), "weird");
        }

        @GetMapping("/t/data-conflict")
        public String dataConflict() {
            throw new DataIntegrityViolationException("could not execute statement; constraint [uk_users_email]");
        }

        @GetMapping("/t/concurrent")
        public String concurrent() {
            throw new OptimisticLockingFailureException("row was updated");
        }

        @GetMapping("/t/upstream-down")
        public String upstreamDown() {
            throw new ResourceAccessException("I/O error on GET request");
        }

        @GetMapping("/t/upstream-error")
        public String upstreamError() {
            throw new RestClientResponseException("boom", 500, "Server Error", null,
                    new byte[0], StandardCharsets.UTF_8);
        }

        @GetMapping("/t/boom")
        public String boom() {
            throw new IllegalStateException("could not connect to jdbc:mysql://db:3306/shop");
        }
    }
}
