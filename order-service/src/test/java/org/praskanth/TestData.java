package org.praskanth;

import org.praskanth.dto.AddressResponse;
import org.praskanth.dto.CartItemResponse;
import org.praskanth.dto.CartResponse;
import org.praskanth.dto.UserResponse;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderItem;
import org.praskanth.entity.OrderStatus;
import org.praskanth.security.gateway.GatewayUser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Small shared builders for order-service tests. */
public final class TestData {

    private TestData() {
    }

    public static AddressResponse address(Long id, boolean isDefault) {
        return AddressResponse.builder()
                .id(id)
                .recipientName("Asha Rao")
                .streetLine1("12 MG Road")
                .streetLine2("Flat 4")
                .city("Bengaluru")
                .state("KA")
                .postalCode("560001")
                .country("India")
                .addressType("HOME")
                .isDefault(isDefault)
                .build();
    }

    public static UserResponse user(Long id, List<AddressResponse> addresses) {
        return UserResponse.builder()
                .id(id)
                .email("asha@example.com")
                .name("Asha")
                .addresses(addresses)
                .build();
    }

    public static CartItemResponse cartItem(Long sellerId, String productId, int qty, String unit, String totalMrp, String totalSelling) {
        return CartItemResponse.builder()
                .id(1L)
                .productId(productId)
                .productName("Product " + productId)
                .imageUrl("http://img/" + productId)
                .quantity(qty)
                .sellerId(sellerId)
                .unitSellingPrice(new BigDecimal(unit))
                .totalMrpPrice(new BigDecimal(totalMrp))
                .totalSellingPrice(new BigDecimal(totalSelling))
                .build();
    }

    public static CartResponse cart(String couponCode, CartItemResponse... items) {
        return CartResponse.builder()
                .cartItems(new ArrayList<>(List.of(items)))
                .couponCode(couponCode)
                .build();
    }

    public static Order order(Long id, Long userId, Long sellerId, OrderStatus status) {
        return Order.builder()
                .id(id)
                .userId(userId)
                .sellerId(sellerId)
                .status(status)
                .totalAmount(new BigDecimal("100.00"))
                .items(new ArrayList<>())
                .build();
    }

    public static OrderItem orderItem(String productId, String variantId, int qty, String unitPrice) {
        return OrderItem.builder()
                .productId(productId)
                .variantId(variantId)
                .productName("Name " + productId)
                .quantity(qty)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }

    public static GatewayUser customer(Long id) {
        return new GatewayUser(id, "u" + id + "@example.com", Set.of("CUSTOMER"));
    }

    public static GatewayUser admin(Long id) {
        return new GatewayUser(id, "admin@example.com", Set.of("ADMIN"));
    }
}
