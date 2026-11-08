package org.praskanth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/** Login and "who am I" - the front door of the whole system. */
class AuthApiTest extends ApiTestBase {

    @Test
    @DisplayName("login with the right credentials returns a token and the ADMIN role")
    void loginSucceeds() {
        org.junit.jupiter.api.Assumptions.assumeTrue(!ADMIN_EMAIL.isBlank(), "ADMIN_EMAIL not set");
        anonymous()
                .body(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
        .when()
                .post("/api/v1/auth/login")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.token", not(emptyOrNullString()))
                .body("data.email", equalToIgnoringCase(ADMIN_EMAIL))
                .body("data.roles", hasItem("ADMIN"));
    }

    @Test
    @DisplayName("a wrong password is refused and no token is leaked")
    void wrongPasswordIsRefused() {
        anonymous()
                .body(Map.of("email", "nobody-" + System.nanoTime() + "@example.com", "password", "Wrong@12345"))
        .when()
                .post("/api/v1/auth/login")
        .then()
                .statusCode(anyOf(is(400), is(401), is(403)))
                .body("success", is(false))
                .body("data", nullValue())
                .body("error.message", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("login with an empty body fails validation (400), it is not a server error")
    void emptyBodyIsABadRequest() {
        anonymous()
                .body("{}")
        .when()
                .post("/api/v1/auth/login")
        .then()
                .statusCode(400)
                .body("success", is(false));
    }

    @Test
    @DisplayName("login with a badly formed e-mail fails validation (400)")
    void malformedEmailIsABadRequest() {
        anonymous()
                .body(Map.of("email", "not-an-email", "password", "Whatever@123"))
        .when()
                .post("/api/v1/auth/login")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("/me without a token is 401 from the gateway")
    void meNeedsAToken() {
        anonymous()
        .when()
                .get("/api/v1/auth/me")
        .then()
                .statusCode(401)
                .body("success", is(false));
    }

    @Test
    @DisplayName("/me with a garbage token is 401 (token is checked, not just present)")
    void meRejectsAForgedToken() {
        anonymous()
                .header("Authorization", "Bearer this.is.not-a-real-token")
        .when()
                .get("/api/v1/auth/me")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("/me with a valid token returns the signed-in admin")
    void meReturnsTheCurrentUser() {
        asAdmin()
        .when()
                .get("/api/v1/auth/me")
        .then()
                .statusCode(200)
                .body("data.email", equalToIgnoringCase(ADMIN_EMAIL));
    }
}
