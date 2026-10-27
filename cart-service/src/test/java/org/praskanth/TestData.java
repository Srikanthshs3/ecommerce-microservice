package org.praskanth;

import org.praskanth.dto.AddItemRequest;
import org.praskanth.dto.ProductResponse;
import org.praskanth.modal.Cart;
import org.praskanth.modal.CartItem;
import org.praskanth.security.gateway.GatewayUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small shared builders for the cart-service tests. */
public final class TestData {

    private TestData() {
    }

    public static GatewayUser user(long id) {
        return new GatewayUser(id, "user" + id + "@example.com", Set.of("CUSTOMER"));
    }

    public static Cart emptyCart(long id, long userId) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setUserEmail("user" + userId + "@example.com");
        cart.setTotalMrpPrice(BigDecimal.ZERO);
        cart.setTotalSellingPrice(BigDecimal.ZERO);
        cart.setTotalItems(0);
        return cart;
    }

    /** A line already attached to the cart (added to its item set). Unit prices in whole currency units. */
    public static CartItem lineInCart(Cart cart, long itemId, long sellerId, int qty, long unitMrp, long unitSelling) {
        CartItem item = new CartItem();
        item.setId(itemId);
        item.setCart(cart);
        item.setUserId(cart.getUserId());
        item.setUserEmail(cart.getUserEmail());
        item.setProductId("p" + itemId);
        item.setProductName("Product " + itemId);
        item.setSellerId(sellerId);
        item.setQuantity(qty);
        item.setUnitMrpPrice(BigDecimal.valueOf(unitMrp));
        item.setUnitSellingPrice(BigDecimal.valueOf(unitSelling));
        item.setTotalMrpPrice(BigDecimal.valueOf(unitMrp * qty));
        item.setTotalSellingPrice(BigDecimal.valueOf(unitSelling * qty));
        cart.getCartItems().add(item);
        return item;
    }

    public static ProductResponse simpleProduct(String id, long mrp, long selling) {
        ProductResponse p = new ProductResponse();
        p.setProductId(id);
        p.setName("Shirt");
        p.setSellerId(7L);
        p.setMrpPrice(BigDecimal.valueOf(mrp));
        p.setSellingPrice(BigDecimal.valueOf(selling));
        p.setImageUrl(List.of("main.png", "second.png"));
        return p;
    }

    public static ProductResponse.Variant variant(String id, String label, long mrp, long selling, List<String> images) {
        ProductResponse.Variant v = new ProductResponse.Variant();
        v.setId(id);
        v.setLabel(label);
        v.setOptions(Map.of("Colour", "Blue"));
        v.setMrpPrice(BigDecimal.valueOf(mrp));
        v.setSellingPrice(BigDecimal.valueOf(selling));
        v.setImageUrl(images);
        return v;
    }

    public static AddItemRequest addRequest(String productId, String variantId, String size, int qty) {
        AddItemRequest r = new AddItemRequest();
        r.setProductId(productId);
        r.setVariantId(variantId);
        r.setSize(size);
        r.setQuantity(qty);
        return r;
    }
}
