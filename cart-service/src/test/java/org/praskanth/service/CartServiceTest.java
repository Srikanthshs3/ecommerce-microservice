package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.config.CouponApiClient;
import org.praskanth.config.InventoryApiClient;
import org.praskanth.config.ProductApiClient;
import org.praskanth.dto.CartItemResponse;
import org.praskanth.dto.CartResponse;
import org.praskanth.dto.CouponLine;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.ProductResponse;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.modal.Cart;
import org.praskanth.modal.CartItem;
import org.praskanth.repository.CartItemRepository;
import org.praskanth.repository.CartRepository;
import org.praskanth.security.gateway.GatewayUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.praskanth.TestData.addRequest;
import static org.praskanth.TestData.emptyCart;
import static org.praskanth.TestData.lineInCart;
import static org.praskanth.TestData.simpleProduct;
import static org.praskanth.TestData.user;
import static org.praskanth.TestData.variant;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private InventoryApiClient inventoryApiClient;
    @Mock
    private ProductApiClient productApiClient;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private CouponApiClient couponApiClient;

    @InjectMocks
    private CartService service;

    private GatewayUser user;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = user(1L);
        cart = emptyCart(10L, 1L);
    }

    private static CouponResult valid(String code, String discount) {
        CouponResult r = new CouponResult();
        r.setValid(true);
        r.setCode(code);
        r.setDiscountAmount(new BigDecimal(discount));
        return r;
    }

    private static CouponResult invalid(String message) {
        CouponResult r = new CouponResult();
        r.setValid(false);
        r.setMessage(message);
        return r;
    }

    private void existingCart() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    }

    private void saveItemReturnsArgument() {
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------ addItem

    @Test
    @DisplayName("addItem: simple product creates a new line with unit and total prices and updates cart totals")
    void addItem_simpleProductNewLine_createsLineAndRecalculatesTotals() {
        // Given
        when(productApiClient.getProductById("p1")).thenReturn(simpleProduct("p1", 100, 80));
        when(inventoryApiClient.checkAvailability("p1", 2)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndSize(cart, "p1", "M")).thenReturn(null);
        saveItemReturnsArgument();

        // When
        CartItemResponse response = service.addItem(user, addRequest("p1", null, "M", 2));

        // Then
        assertThat(response.getProductId()).isEqualTo("p1");
        assertThat(response.getProductName()).isEqualTo("Shirt");
        assertThat(response.getSellerId()).isEqualTo(7L);
        assertThat(response.getImageUrl()).isEqualTo("main.png");
        assertThat(response.getSize()).isEqualTo("M");
        assertThat(response.getVariantId()).isNull();
        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getUnitMrpPrice()).isEqualByComparingTo("100");
        assertThat(response.getUnitSellingPrice()).isEqualByComparingTo("80");
        assertThat(response.getTotalMrpPrice()).isEqualByComparingTo("200");
        assertThat(response.getTotalSellingPrice()).isEqualByComparingTo("160");

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCart()).isSameAs(cart);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserEmail()).isEqualTo("user1@example.com");

        assertThat(cart.getCartItems()).hasSize(1);
        assertThat(cart.getTotalItems()).isEqualTo(2);
        assertThat(cart.getTotalMrpPrice()).isEqualByComparingTo("200");
        assertThat(cart.getTotalSellingPrice()).isEqualByComparingTo("160");
        assertThat(cart.getDiscount()).isEqualByComparingTo("20.00");
        verify(cartRepository).save(cart);
        verify(couponApiClient, never()).validate(anyString(), anyList());
    }

    @Test
    @DisplayName("addItem: simple product without pictures stores a null picture")
    void addItem_productWithoutImages_pictureIsNull() {
        ProductResponse product = simpleProduct("p1", 100, 100);
        product.setImageUrl(null);
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndSize(cart, "p1", null)).thenReturn(null);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", null, null, 1));

        assertThat(response.getImageUrl()).isNull();
        // mrp == selling: no discount percentage
        assertThat(cart.getDiscount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("addItem: product with an empty picture list stores a null picture")
    void addItem_productWithEmptyImages_pictureIsNull() {
        ProductResponse product = simpleProduct("p1", 100, 100);
        product.setImageUrl(List.of());
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndSize(cart, "p1", null)).thenReturn(null);
        saveItemReturnsArgument();

        assertThat(service.addItem(user, addRequest("p1", null, null, 1)).getImageUrl()).isNull();
    }

    @Test
    @DisplayName("addItem: existing simple line has its quantity incremented using the unit price")
    void addItem_existingLine_incrementsQuantityAndTotals() {
        CartItem existing = lineInCart(cart, 5L, 7L, 1, 100, 80);
        existing.setProductId("p1");
        existing.setImageUrl(null);
        when(productApiClient.getProductById("p1")).thenReturn(simpleProduct("p1", 100, 80));
        when(inventoryApiClient.checkAvailability("p1", 2)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndSize(cart, "p1", "M")).thenReturn(existing);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", null, "M", 2));

        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getTotalMrpPrice()).isEqualByComparingTo("300");
        assertThat(response.getTotalSellingPrice()).isEqualByComparingTo("240");
        // a line created before pictures existed gets one now
        assertThat(response.getImageUrl()).isEqualTo("main.png");
        assertThat(cart.getTotalItems()).isEqualTo(3);
        assertThat(cart.getTotalSellingPrice()).isEqualByComparingTo("240");
    }

    @Test
    @DisplayName("addItem: existing line keeps its own picture")
    void addItem_existingLineWithPicture_keepsPicture() {
        CartItem existing = lineInCart(cart, 5L, 7L, 1, 100, 80);
        existing.setProductId("p1");
        existing.setImageUrl("old.png");
        when(productApiClient.getProductById("p1")).thenReturn(simpleProduct("p1", 100, 80));
        when(inventoryApiClient.checkAvailability("p1", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndSize(cart, "p1", null)).thenReturn(existing);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", null, null, 1));

        assertThat(response.getImageUrl()).isEqualTo("old.png");
        assertThat(response.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("addItem: new cart is created when the user has none")
    void addItem_noCartYet_createsCart() {
        when(productApiClient.getProductById("p1")).thenReturn(simpleProduct("p1", 100, 80));
        when(inventoryApiClient.checkAvailability("p1", 1)).thenReturn(true);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByCartAndProductIdAndSize(any(Cart.class), eq("p1"), eq(null))).thenReturn(null);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", null, null, 1));

        assertThat(response.getQuantity()).isEqualTo(1);
        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Cart created = captor.getAllValues().get(0);
        assertThat(created.getUserId()).isEqualTo(1L);
        assertThat(created.getUserEmail()).isEqualTo("user1@example.com");
    }

    @Test
    @DisplayName("addItem: variant product uses the variant price, label, picture and productId:variantId stock key")
    void addItem_variantProduct_usesVariantData() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setOptionNames(List.of("Colour", "Size"));
        product.setVariants(List.of(
                variant("v1", "Blue / M", 150, 120, List.of("blue.png")),
                variant("v2", "Red / M", 160, 130, List.of())));
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1:v1", 2)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndVariantId(cart, "p1", "v1")).thenReturn(null);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", "v1", "ignored", 2));

        assertThat(response.getVariantId()).isEqualTo("v1");
        assertThat(response.getVariantLabel()).isEqualTo("Blue / M");
        assertThat(response.getSize()).isNull();
        assertThat(response.getImageUrl()).isEqualTo("blue.png");
        assertThat(response.getUnitMrpPrice()).isEqualByComparingTo("150");
        assertThat(response.getUnitSellingPrice()).isEqualByComparingTo("120");
        assertThat(response.getTotalSellingPrice()).isEqualByComparingTo("240");
        verify(cartItemRepository, never()).findByCartAndProductIdAndSize(any(), anyString(), any());
    }

    @Test
    @DisplayName("addItem: variant without pictures falls back to the product's main picture")
    void addItem_variantWithoutImages_fallsBackToProductPicture() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setVariants(List.of(variant("v2", "Red / M", 160, 130, List.of())));
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1:v2", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndVariantId(cart, "p1", "v2")).thenReturn(null);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", "v2", null, 1));

        assertThat(response.getImageUrl()).isEqualTo("main.png");
    }

    @Test
    @DisplayName("addItem: variant with null picture list falls back to the product's main picture")
    void addItem_variantWithNullImages_fallsBackToProductPicture() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setVariants(List.of(variant("v2", "Red / M", 160, 130, null)));
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1:v2", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndVariantId(cart, "p1", "v2")).thenReturn(null);
        saveItemReturnsArgument();

        assertThat(service.addItem(user, addRequest("p1", "v2", null, 1)).getImageUrl()).isEqualTo("main.png");
    }

    @Test
    @DisplayName("addItem: existing variant line is incremented")
    void addItem_existingVariantLine_incrementsQuantity() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setVariants(List.of(variant("v1", "Blue / M", 150, 120, List.of("blue.png"))));
        CartItem existing = lineInCart(cart, 5L, 7L, 1, 150, 120);
        existing.setProductId("p1");
        existing.setVariantId("v1");
        existing.setImageUrl("blue.png");
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1:v1", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndVariantId(cart, "p1", "v1")).thenReturn(existing);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", "v1", null, 1));

        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getTotalSellingPrice()).isEqualByComparingTo("240");
    }

    @Test
    @DisplayName("addItem: variant product without a chosen variant is rejected and names the options")
    void addItem_variantProductNoVariantChosen_throwsWithOptionNames() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setOptionNames(List.of("Colour", "Size"));
        product.setVariants(List.of(variant("v1", "Blue / M", 150, 120, List.of())));
        when(productApiClient.getProductById("p1")).thenReturn(product);

        assertThatThrownBy(() -> service.addItem(user, addRequest("p1", null, null, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please choose Colour and Size first.");
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: blank variant id on a variant product uses the generic option wording")
    void addItem_variantProductBlankVariantNoOptionNames_throwsGenericMessage() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setOptionNames(null);
        product.setVariants(List.of(variant("v1", "Blue / M", 150, 120, List.of())));
        when(productApiClient.getProductById("p1")).thenReturn(product);

        assertThatThrownBy(() -> service.addItem(user, addRequest("p1", "  ", null, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please choose an option first.");
    }

    @Test
    @DisplayName("addItem: unknown variant id is rejected")
    void addItem_unknownVariant_throws() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setVariants(List.of(variant("v1", "Blue / M", 150, 120, List.of())));
        when(productApiClient.getProductById("p1")).thenReturn(product);

        assertThatThrownBy(() -> service.addItem(user, addRequest("p1", "zzz", null, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no longer available");
        verify(inventoryApiClient, never()).checkAvailability(anyString(), any());
    }

    @Test
    @DisplayName("addItem: variant id on a product without variants is rejected")
    void addItem_variantOnSimpleProduct_throws() {
        when(productApiClient.getProductById("p1")).thenReturn(simpleProduct("p1", 100, 80));

        assertThatThrownBy(() -> service.addItem(user, addRequest("p1", "v1", null, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This product has no variants.");
    }

    @Test
    @DisplayName("addItem: empty variant list is treated as a simple product, blank variant id is ignored")
    void addItem_emptyVariantListBlankVariantId_treatedAsSimple() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setVariants(List.of());
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1", 1)).thenReturn(true);
        existingCart();
        when(cartItemRepository.findByCartAndProductIdAndSize(cart, "p1", null)).thenReturn(null);
        saveItemReturnsArgument();

        CartItemResponse response = service.addItem(user, addRequest("p1", "", null, 1));

        assertThat(response.getVariantId()).isNull();
    }

    @Test
    @DisplayName("addItem: out of stock simple product is rejected and nothing is saved")
    void addItem_outOfStock_throws() {
        when(productApiClient.getProductById("p1")).thenReturn(simpleProduct("p1", 100, 80));
        when(inventoryApiClient.checkAvailability("p1", 5)).thenReturn(false);

        assertThatThrownBy(() -> service.addItem(user, addRequest("p1", null, null, 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Requested quantity is out of stock for product: Shirt");
        verify(cartItemRepository, never()).save(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: out of stock variant mentions the variant label")
    void addItem_outOfStockVariant_messageHasLabel() {
        ProductResponse product = simpleProduct("p1", 100, 80);
        product.setVariants(List.of(variant("v1", "Blue / M", 150, 120, List.of())));
        when(productApiClient.getProductById("p1")).thenReturn(product);
        when(inventoryApiClient.checkAvailability("p1:v1", 1)).thenReturn(false);

        assertThatThrownBy(() -> service.addItem(user, addRequest("p1", "v1", null, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Requested quantity is out of stock for product: Shirt (Blue / M)");
    }

    // ------------------------------------------------------------------ updateCart

    @Test
    @DisplayName("updateCart: recalculates line and cart totals from the unit price")
    void updateCart_ownItem_recalculatesTotals() {
        CartItem item = lineInCart(cart, 5L, 7L, 1, 100, 80);
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
        saveItemReturnsArgument();

        CartItemResponse response = service.updateCart(1L, 5L, 4);

        assertThat(response.getQuantity()).isEqualTo(4);
        assertThat(response.getTotalMrpPrice()).isEqualByComparingTo("400");
        assertThat(response.getTotalSellingPrice()).isEqualByComparingTo("320");
        assertThat(cart.getTotalItems()).isEqualTo(4);
        assertThat(cart.getDiscount()).isEqualByComparingTo("20.00");
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("updateCart: unknown item id is rejected")
    void updateCart_unknownItem_throws() {
        when(cartItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCart(1L, 99L, 2))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("That item is no longer in your cart.");
    }

    @Test
    @DisplayName("updateCart: item of another user is rejected and not modified")
    void updateCart_otherUsersItem_throws() {
        CartItem item = lineInCart(cart, 5L, 7L, 1, 100, 80);
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateCart(2L, 5L, 4))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You are not allowed to update this cart item");
        assertThat(item.getQuantity()).isEqualTo(1);
        verify(cartItemRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ deleteCartItem

    @Test
    @DisplayName("deleteCartItem: removes the line and recalculates the cart")
    void deleteCartItem_ownItem_removesAndRecalculates() {
        CartItem keep = lineInCart(cart, 6L, 7L, 1, 50, 40);
        CartItem remove = lineInCart(cart, 5L, 7L, 2, 100, 80);
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(remove));

        service.deleteCartItem(1L, 5L);

        verify(cartItemRepository).delete(remove);
        assertThat(cart.getCartItems()).containsExactly(keep);
        assertThat(cart.getTotalItems()).isEqualTo(1);
        assertThat(cart.getTotalSellingPrice()).isEqualByComparingTo("40");
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("deleteCartItem: other user's item is rejected")
    void deleteCartItem_otherUsersItem_throws() {
        CartItem item = lineInCart(cart, 5L, 7L, 1, 100, 80);
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.deleteCartItem(2L, 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You are not allowed to delete this cart item");
        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    @Test
    @DisplayName("deleteCartItem: deleting the last line drops the coupon without calling coupon-service")
    void deleteCartItem_lastLineWithCoupon_dropsCoupon() {
        CartItem item = lineInCart(cart, 5L, 7L, 1, 100, 80);
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(new BigDecimal("10"));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        service.deleteCartItem(1L, 5L);

        assertThat(cart.getCartItems()).isEmpty();
        assertThat(cart.getCouponCode()).isNull();
        assertThat(cart.getCouponDiscount()).isEqualByComparingTo("0");
        verify(couponApiClient, never()).validate(anyString(), anyList());
    }

    // ------------------------------------------------------------------ clearCart

    @Test
    @DisplayName("clearCart: empties items, drops the coupon and zeroes totals")
    void clearCart_cartWithItems_emptiesCart() {
        lineInCart(cart, 5L, 7L, 2, 100, 80);
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(new BigDecimal("10"));
        existingCart();

        service.clearCart(user);

        assertThat(cart.getCartItems()).isEmpty();
        assertThat(cart.getCouponCode()).isNull();
        assertThat(cart.getCouponDiscount()).isEqualByComparingTo("0");
        assertThat(cart.getTotalItems()).isZero();
        assertThat(cart.getTotalSellingPrice()).isEqualByComparingTo("0");
        assertThat(cart.getTotalMrpPrice()).isEqualByComparingTo("0");
        assertThat(cart.getDiscount()).isEqualByComparingTo("0");
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("clearCart: is a no-op when the user has no cart")
    void clearCart_noCart_noop() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        service.clearCart(user);

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("clearCart: is idempotent on an already empty cart")
    void clearCart_emptyCart_staysEmpty() {
        existingCart();

        service.clearCart(user);
        service.clearCart(user);

        assertThat(cart.getCartItems()).isEmpty();
        assertThat(cart.getTotalItems()).isZero();
    }

    // ------------------------------------------------------------------ applyCoupon / removeCoupon

    @Test
    @DisplayName("applyCoupon: empty cart is rejected without calling coupon-service")
    void applyCoupon_emptyCart_throws() {
        existingCart();

        assertThatThrownBy(() -> service.applyCoupon(user, "SAVE10"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cart is empty");
        verify(couponApiClient, never()).validate(anyString(), anyList());
    }

    @Test
    @DisplayName("applyCoupon: valid coupon is stored and payable amount is reduced; lines are grouped per seller")
    void applyCoupon_validCoupon_storesCouponAndGroupsBySeller() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        lineInCart(cart, 2L, 7L, 1, 50, 40);
        lineInCart(cart, 3L, 8L, 1, 30, 30);
        cart.setTotalSellingPrice(new BigDecimal("150"));
        cart.setTotalMrpPrice(new BigDecimal("180"));
        existingCart();
        when(couponApiClient.validate(eq("SAVE10"), anyList())).thenReturn(valid("SAVE10", "15"));

        CartResponse response = service.applyCoupon(user, "SAVE10");

        assertThat(cart.getCouponCode()).isEqualTo("SAVE10");
        assertThat(cart.getCouponDiscount()).isEqualByComparingTo("15");
        assertThat(response.getCouponCode()).isEqualTo("SAVE10");
        assertThat(response.getCouponDiscount()).isEqualByComparingTo("15");
        assertThat(response.getPayableAmount()).isEqualByComparingTo("135");
        verify(cartRepository).save(cart);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CouponLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(couponApiClient).validate(eq("SAVE10"), captor.capture());
        List<CouponLine> lines = captor.getValue();
        assertThat(lines).hasSize(2);
        BigDecimal seller7 = lines.stream().filter(l -> l.sellerId().equals(7L)).findFirst().get().amount();
        BigDecimal seller8 = lines.stream().filter(l -> l.sellerId().equals(8L)).findFirst().get().amount();
        assertThat(seller7).isEqualByComparingTo("120");
        assertThat(seller8).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("applyCoupon: invalid coupon is rejected with coupon-service's reason and not stored")
    void applyCoupon_invalidCoupon_throwsWithReason() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        existingCart();
        when(couponApiClient.validate(eq("BAD"), anyList())).thenReturn(invalid("Coupon has expired"));

        assertThatThrownBy(() -> service.applyCoupon(user, "BAD"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Coupon has expired");
        assertThat(cart.getCouponCode()).isNull();
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyCoupon: coupon-service outage is propagated to the caller")
    void applyCoupon_couponServiceDown_propagates() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        existingCart();
        when(couponApiClient.validate(eq("SAVE10"), anyList()))
                .thenThrow(new ServiceUnavailableException("down"));

        assertThatThrownBy(() -> service.applyCoupon(user, "SAVE10"))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(cart.getCouponCode()).isNull();
    }

    @Test
    @DisplayName("removeCoupon: clears code and discount")
    void removeCoupon_cartWithCoupon_clears() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        cart.setTotalSellingPrice(new BigDecimal("80"));
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(new BigDecimal("10"));
        existingCart();

        CartResponse response = service.removeCoupon(user);

        assertThat(cart.getCouponCode()).isNull();
        assertThat(response.getCouponCode()).isNull();
        assertThat(response.getCouponDiscount()).isEqualByComparingTo("0");
        assertThat(response.getPayableAmount()).isEqualByComparingTo("80");
        verify(cartRepository).save(cart);
    }

    // ------------------------------------------------------------------ getUserCart / coupon refresh

    @Test
    @DisplayName("getUserCart: cart without coupon is returned without any save or coupon call")
    void getUserCart_noCoupon_returnsCartAsIs() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        cart.setTotalSellingPrice(new BigDecimal("80"));
        existingCart();

        CartResponse response = service.getUserCart(user);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCartItems()).hasSize(1);
        verify(cartRepository, never()).save(any());
        verify(couponApiClient, never()).validate(anyString(), anyList());
    }

    @Test
    @DisplayName("getUserCart: creates an empty cart when the user has none")
    void getUserCart_noCart_createsEmptyCart() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = service.getUserCart(user);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getCartItems()).isEmpty();
        assertThat(response.getPayableAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getUserCart: coupon still valid refreshes the discount amount")
    void getUserCart_couponStillValid_updatesDiscount() {
        lineInCart(cart, 1L, 7L, 2, 100, 80);
        cart.setTotalSellingPrice(new BigDecimal("160"));
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(new BigDecimal("5"));
        existingCart();
        when(couponApiClient.validate(eq("SAVE10"), anyList())).thenReturn(valid("SAVE10", "16"));

        CartResponse response = service.getUserCart(user);

        assertThat(response.getCouponDiscount()).isEqualByComparingTo("16");
        assertThat(response.getPayableAmount()).isEqualByComparingTo("144");
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("getUserCart: coupon no longer valid is removed")
    void getUserCart_couponNoLongerValid_removesCoupon() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        cart.setTotalSellingPrice(new BigDecimal("80"));
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(new BigDecimal("10"));
        existingCart();
        when(couponApiClient.validate(eq("SAVE10"), anyList())).thenReturn(invalid("Minimum order not met"));

        CartResponse response = service.getUserCart(user);

        assertThat(response.getCouponCode()).isNull();
        assertThat(response.getCouponDiscount()).isEqualByComparingTo("0");
        assertThat(response.getPayableAmount()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("getUserCart: coupon-service failure keeps the previous discount")
    void getUserCart_couponServiceFails_keepsPreviousDiscount() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        cart.setTotalSellingPrice(new BigDecimal("80"));
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(new BigDecimal("10"));
        existingCart();
        when(couponApiClient.validate(eq("SAVE10"), anyList())).thenThrow(new RuntimeException("timeout"));

        CartResponse response = service.getUserCart(user);

        assertThat(response.getCouponCode()).isEqualTo("SAVE10");
        assertThat(response.getCouponDiscount()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("getUserCart: coupon-service failure with null previous discount falls back to zero")
    void getUserCart_couponServiceFailsNullPrevious_zeroDiscount() {
        lineInCart(cart, 1L, 7L, 1, 100, 80);
        cart.setTotalSellingPrice(new BigDecimal("80"));
        cart.setCouponCode("SAVE10");
        cart.setCouponDiscount(null);
        existingCart();
        when(couponApiClient.validate(eq("SAVE10"), anyList())).thenThrow(new RuntimeException("timeout"));

        CartResponse response = service.getUserCart(user);

        assertThat(response.getCouponDiscount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("updateCart: when coupon-service fails the discount is capped at the new cart total")
    void updateCart_couponServiceFailsAndTotalDropped_discountCappedAtTotal() {
        CartItem item = lineInCart(cart, 5L, 7L, 5, 100, 80);
        cart.setCouponCode("BIG");
        cart.setCouponDiscount(new BigDecimal("300"));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));
        saveItemReturnsArgument();
        when(couponApiClient.validate(eq("BIG"), anyList())).thenThrow(new RuntimeException("down"));

        service.updateCart(1L, 5L, 1);

        // new selling total is 80, previous discount was 300
        assertThat(cart.getCouponDiscount()).isEqualByComparingTo("80");
    }

    // ------------------------------------------------------------------ mappers / helpers

    @Test
    @DisplayName("toCartResponse: null cart gives null")
    void toCartResponse_null_returnsNull() {
        assertThat(service.toCartResponse(null)).isNull();
    }

    @Test
    @DisplayName("toCartItemResponse: null item gives null")
    void toCartItemResponse_null_returnsNull() {
        assertThat(service.toCartItemResponse(null)).isNull();
    }

    @Test
    @DisplayName("toCartResponse: payable amount never goes below zero")
    void toCartResponse_couponLargerThanTotal_payableIsZero() {
        cart.setTotalSellingPrice(new BigDecimal("50"));
        cart.setCouponDiscount(new BigDecimal("80"));

        CartResponse response = service.toCartResponse(cart);

        assertThat(response.getPayableAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("toCartResponse: null totals, null coupon discount and null item set are handled")
    void toCartResponse_nullFields_defaultsToZeroAndEmptyList() {
        cart.setTotalSellingPrice(null);
        cart.setCouponDiscount(null);
        cart.setCartItems(null);

        CartResponse response = service.toCartResponse(cart);

        assertThat(response.getCartItems()).isEmpty();
        assertThat(response.getCouponDiscount()).isEqualByComparingTo("0");
        assertThat(response.getPayableAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("findCartItemById: returns the item when present")
    void findCartItemById_present_returnsItem() {
        CartItem item = lineInCart(cart, 5L, 7L, 1, 100, 80);
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThat(service.findCartItemById(5L)).isSameAs(item);
    }
}
