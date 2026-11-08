package org.praskanth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.is;

/** The cart belongs to a signed-in customer: every call must carry a token. */
class CartApiTest extends ApiTestBase {

    @Test
    @DisplayName("reading the cart without signing in is 401")
    void getCartNeedsLogin() {
        anonymous().when().get("/api/v1/cart").then().statusCode(401).body("success", is(false));
    }

    @Test
    @DisplayName("adding an item without signing in is 401")
    void addItemNeedsLogin() {
        anonymous()
                .body(Map.of("productId", "x", "quantity", 1))
        .when()
                .post("/api/v1/cart")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("applying a coupon without signing in is 401")
    void applyCouponNeedsLogin() {
        anonymous()
                .body(Map.of("code", "ANYTHING"))
        .when()
                .post("/api/v1/cart/coupon")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("clearing the cart without signing in is 401")
    void clearCartNeedsLogin() {
        anonymous().when().delete("/api/v1/cart").then().statusCode(401);
    }
}
