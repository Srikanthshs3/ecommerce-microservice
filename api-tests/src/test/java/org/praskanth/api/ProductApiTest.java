package org.praskanth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/** Browsing is public; changing the catalogue is not. */
class ProductApiTest extends ApiTestBase {

    @Test
    @DisplayName("anyone can list products without signing in")
    void listIsPublic() {
        anonymous()
        .when()
                .get("/api/v1/product/get-all")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", instanceOf(java.util.List.class));
    }

    @Test
    @DisplayName("the limit parameter caps the number of products returned")
    void limitIsHonoured() {
        anonymous()
                .queryParam("limit", 1)
        .when()
                .get("/api/v1/product/get-all")
        .then()
                .statusCode(200)
                .body("data.size()", lessThanOrEqualTo(1));
    }

    @Test
    @DisplayName("searching for a name that cannot exist gives an empty list, not an error")
    void searchWithNoMatch() {
        anonymous()
                .queryParam("name", "zzzz-no-such-product-" + System.nanoTime())
        .when()
                .get("/api/v1/product/search")
        .then()
                .statusCode(200)
                .body("data", empty());
    }

    @Test
    @DisplayName("asking for a product id that does not exist is a 404 with a readable message")
    void unknownProductIs404() {
        anonymous()
        .when()
                .get("/api/v1/product/getProductById/000000000000000000000000")
        .then()
                .statusCode(404)
                .body("success", is(false))
                .body("error.message", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("creating a product without signing in is refused (401)")
    void createNeedsLogin() {
        anonymous()
                .body(Map.of("name", "Sneaky product", "categoryId", "x"))
        .when()
                .post("/api/v1/product/create")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("deleting a product without signing in is refused (401)")
    void deleteNeedsLogin() {
        anonymous()
        .when()
                .delete("/api/v1/product/delete/000000000000000000000000")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("a signed-in admin sending an invalid product gets 400 validation, not 500")
    void invalidProductIsABadRequest() {
        asAdmin()
                .body("{}") // name and categoryId are mandatory
        .when()
                .post("/api/v1/product/create")
        .then()
                .statusCode(400)
                .body("success", is(false));
    }
}
