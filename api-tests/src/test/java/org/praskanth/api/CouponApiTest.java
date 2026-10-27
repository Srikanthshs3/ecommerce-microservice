package org.praskanth.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Coupon life cycle through the gateway. The test creates its own coupon with a unique code and deletes it
 * afterwards, so it never touches the coupons you made by hand and can be run again and again.
 */
class CouponApiTest extends ApiTestBase {

    private Integer createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            asAdmin().delete("/api/v1/coupon/" + createdId).then().statusCode(200);
            createdId = null;
        }
    }

    private static Map<String, Object> newCoupon(String code, boolean listed) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("description", "API test coupon");
        body.put("discountType", "PERCENTAGE");
        body.put("discountValue", 10);
        body.put("maxDiscountAmount", 100);
        body.put("minOrderAmount", 0);
        body.put("usageLimit", 5);
        body.put("active", true);
        body.put("listed", listed);
        return body;
    }

    private static String uniqueCode() {
        return "APITEST" + (System.nanoTime() % 1_000_000_000L);
    }

    @Test
    @DisplayName("the coupon list for customers needs a login")
    void availableNeedsLogin() {
        anonymous().when().get("/api/v1/coupon/available").then().statusCode(401);
    }

    @Test
    @DisplayName("admin creates a listed coupon: 201, code stored in upper case, and it shows up in the manage list")
    void adminCreatesCoupon() {
        String code = uniqueCode().toLowerCase(); // sent in lower case on purpose
        createdId = asAdmin()
                .body(newCoupon(code, true))
        .when()
                .post("/api/v1/coupon")
        .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.code", equalTo(code.toUpperCase()))
                .extract().path("data.id");

        asAdmin()
        .when()
                .get("/api/v1/coupon/manage")
        .then()
                .statusCode(200)
                .body("data.code", hasItem(code.toUpperCase()));
    }

    @Test
    @DisplayName("a listed coupon is visible to a signed-in user in /available (a private one is not)")
    void listedCouponIsVisible() {
        String code = uniqueCode();
        createdId = asAdmin().body(newCoupon(code, true)).post("/api/v1/coupon").then().statusCode(201).extract().path("data.id");

        asAdmin().get("/api/v1/coupon/available").then().statusCode(200).body("data.code", hasItem(code));
    }

    @Test
    @DisplayName("a private (not listed) coupon is NOT shown in /available")
    void privateCouponIsHidden() {
        String code = uniqueCode();
        createdId = asAdmin().body(newCoupon(code, false)).post("/api/v1/coupon").then().statusCode(201).extract().path("data.id");

        asAdmin().get("/api/v1/coupon/available").then().statusCode(200).body("data.code", not(hasItem(code)));
    }

    @Test
    @DisplayName("creating the same code twice is refused with a 4xx (codes are unique)")
    void duplicateCodeIsRefused() {
        String code = uniqueCode();
        createdId = asAdmin().body(newCoupon(code, true)).post("/api/v1/coupon").then().statusCode(201).extract().path("data.id");

        asAdmin().body(newCoupon(code, true)).post("/api/v1/coupon").then().statusCode(allOf(greaterThanOrEqualTo(400), lessThan(500)));
    }

    @Test
    @DisplayName("a zero discount value fails validation (400)")
    void zeroDiscountIsRejected() {
        Map<String, Object> body = newCoupon(uniqueCode(), true);
        body.put("discountValue", 0);
        asAdmin().body(body).post("/api/v1/coupon").then().statusCode(400).body("success", is(false));
    }

    @Test
    @DisplayName("a code with spaces or symbols fails validation (400)")
    void badCodeIsRejected() {
        asAdmin().body(newCoupon("BAD CODE!", true)).post("/api/v1/coupon").then().statusCode(400);
    }

    @Test
    @DisplayName("creating a coupon without signing in is 401")
    void createNeedsLogin() {
        anonymous().body(newCoupon(uniqueCode(), true)).post("/api/v1/coupon").then().statusCode(401);
    }
}
