package org.praskanth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.config.CartApiClient;
import org.praskanth.config.CouponApiClient;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CartResponse;
import org.praskanth.dto.CouponLine;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.OrderRequest;
import org.praskanth.dto.OrderResponse;
import org.praskanth.dto.RedeemRequest;
import org.praskanth.dto.ReleaseRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.outbox.OrderCreatedFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.OrderRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class OrderServiceCreateTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private CartApiClient cartApiClient;
    @Mock
    private CouponApiClient couponApiClient;
    @Mock
    private org.praskanth.notification.OrderNotificationPublisher notificationPublisher;

    @InjectMocks
    private OrderService service;

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "paymentWindowMinutes", 15L);
    }

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubSaveAssignsIds() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ids.incrementAndGet());
            return o;
        });
    }

    private void stubCart(CartResponse cart) {
        when(cartApiClient.getUserCart()).thenReturn(ApiResponse.success(cart));
    }

    private OrderRequest request(Long addressId) {
        OrderRequest r = new OrderRequest();
        r.setAddressId(addressId);
        return r;
    }

    private UserResponse userWithDefaultAddress() {
        return TestData.user(5L, List.of(TestData.address(1L, true)));
    }

    private CouponResult validResult(Long seller1, String amount1, Long seller2, String amount2) {
        CouponResult result = new CouponResult();
        result.setValid(true);
        result.setCode("SAVE10");
        List<CouponResult.Allocation> allocations = new ArrayList<>();
        CouponResult.Allocation a1 = new CouponResult.Allocation();
        a1.setSellerId(seller1);
        a1.setAmount(new BigDecimal(amount1));
        allocations.add(a1);
        if (seller2 != null) {
            CouponResult.Allocation a2 = new CouponResult.Allocation();
            a2.setSellerId(seller2);
            a2.setAmount(new BigDecimal(amount2));
            allocations.add(a2);
        }
        result.setAllocations(allocations);
        return result;
    }

    // ------------------------------------------------------------------ address validation

    @Test
    @DisplayName("create rejects a user without any address (null list)")
    void create_nullAddresses_badRequest() {
        UserResponse user = TestData.user(5L, null);

        assertThatThrownBy(() -> service.create(request(null), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No shipping address");

        verifyNoInteractions(cartApiClient, orderRepository, outboxEventWriter);
    }

    @Test
    void create_emptyAddresses_badRequest() {
        UserResponse user = TestData.user(5L, List.of());

        assertThatThrownBy(() -> service.create(request(null), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No shipping address");
    }

    @Test
    void create_requestedAddressNotOwned_badRequest() {
        UserResponse user = TestData.user(5L, List.of(TestData.address(1L, true)));

        assertThatThrownBy(() -> service.create(request(99L), user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to your account");

        verifyNoInteractions(cartApiClient, orderRepository);
    }

    @Test
    @DisplayName("create uses the explicitly requested address even when another one is the default")
    void create_requestedAddress_isUsed() {
        UserResponse user = TestData.user(5L, List.of(TestData.address(1L, true), TestData.address(2L, false)));
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();

        service.create(request(2L), user);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShippingAddressId()).isEqualTo(2L);
    }

    @Test
    void create_noRequestedAddress_usesDefault() {
        UserResponse user = TestData.user(5L, List.of(TestData.address(1L, false), TestData.address(2L, true)));
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();

        service.create(request(null), user);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShippingAddressId()).isEqualTo(2L);
    }

    @Test
    void create_noDefaultFlagged_fallsBackToFirstAddress() {
        UserResponse user = TestData.user(5L, List.of(TestData.address(3L, false), TestData.address(4L, false)));
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();

        service.create(request(null), user);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShippingAddressId()).isEqualTo(3L);
    }

    @Test
    void create_addressWithoutLine2_isFormattedWithoutIt() {
        var address = TestData.address(1L, true);
        address.setStreetLine2("  ");
        UserResponse user = TestData.user(5L, List.of(address));
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();

        service.create(request(null), user);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShippingAddress())
                .isEqualTo("Asha Rao, 12 MG Road, Bengaluru, KA - 560001, India");
    }

    // ------------------------------------------------------------------ cart validation

    @Test
    void create_cartDataNull_badRequest() {
        stubCart(null);

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cart is empty");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_cartItemsNull_badRequest() {
        CartResponse cart = CartResponse.builder().cartItems(null).build();
        stubCart(cart);

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_cartItemsEmpty_badRequest() {
        stubCart(TestData.cart(null));

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(couponApiClient, outboxEventWriter);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    @DisplayName("create builds one PENDING order with totals, discount, address snapshot and expiry, writes the outbox event and clears the cart")
    void create_singleSellerNoCoupon_happyPath() {
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 2, "400.00", "1000.00", "800.00")));
        stubSaveAssignsIds();
        Instant before = Instant.now();

        List<OrderResponse> result = service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getCustomerEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getCustomerName()).isEqualTo("Asha");
        assertThat(saved.getSellerId()).isEqualTo(7L);
        assertThat(saved.getTotalMrpPrice()).isEqualByComparingTo("1000.00");
        assertThat(saved.getTotalSellingPrice()).isEqualByComparingTo("800.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("800.00");
        assertThat(saved.getDiscount()).isEqualTo(20);
        assertThat(saved.getCouponCode()).isNull();
        assertThat(saved.getCouponDiscount()).isEqualByComparingTo("0");
        assertThat(saved.getCheckoutRef()).isNotBlank();
        assertThat(saved.getShippingAddress())
                .isEqualTo("Asha Rao, 12 MG Road, Flat 4, Bengaluru, KA - 560001, India");
        assertThat(saved.getExpiresAt()).isBetween(before.plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60 + 5));
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getProductId()).isEqualTo("P1");
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getItems().get(0).getUnitPrice()).isEqualByComparingTo("400.00");
        assertThat(saved.getItems().get(0).getOrder()).isSameAs(saved);

        ArgumentCaptor<Object> fieldsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(eq("OrderCreatedEvent"), eq("order.created"), eq("101"), fieldsCaptor.capture());
        OrderCreatedFields fields = (OrderCreatedFields) fieldsCaptor.getValue();
        assertThat(fields.orderId()).isEqualTo(101L);
        assertThat(fields.userId()).isEqualTo(5L);
        assertThat(fields.totalAmount()).isEqualByComparingTo("800.00");
        assertThat(fields.items()).hasSize(1);
        assertThat(fields.items().get(0).productId()).isEqualTo("P1");
        assertThat(fields.items().get(0).quantity()).isEqualTo(2);

        verify(cartApiClient).clearCart();
        verifyNoInteractions(couponApiClient);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.get(0).getId()).isEqualTo(101L);
        assertThat(result.get(0).getItems()).hasSize(1);
    }

    @Test
    void create_variantItem_usesStockIdInEvent() {
        var item = TestData.cartItem(7L, "P1", 1, "50.00", "50.00", "50.00");
        item.setVariantId("V2");
        item.setVariantLabel("Blue / M");
        stubCart(TestData.cart(null, item));
        stubSaveAssignsIds();

        service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Object> fieldsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(anyString(), anyString(), anyString(), fieldsCaptor.capture());
        OrderCreatedFields fields = (OrderCreatedFields) fieldsCaptor.getValue();
        assertThat(fields.items().get(0).productId()).isEqualTo("P1:V2");
    }

    @Test
    void create_mrpNotAboveSelling_discountIsZero() {
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();

        service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getDiscount()).isZero();
    }

    @Test
    void create_discountIsRoundedToTwoDecimalsBeforePercent() {
        // (300-200)/300 = 0.33 -> 33
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "200.00", "300.00", "200.00")));
        stubSaveAssignsIds();

        service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getDiscount()).isEqualTo(33);
    }

    @Test
    void create_twoSellers_twoOrdersSharingOneCheckoutRef() {
        stubCart(TestData.cart(null,
                TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00"),
                TestData.cartItem(8L, "P2", 1, "50.00", "50.00", "50.00"),
                TestData.cartItem(7L, "P3", 1, "10.00", "10.00", "10.00")));
        stubSaveAssignsIds();

        List<OrderResponse> result = service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(captor.capture());
        List<Order> saved = captor.getAllValues();
        assertThat(saved.get(0).getCheckoutRef()).isEqualTo(saved.get(1).getCheckoutRef());
        assertThat(saved).extracting(Order::getSellerId).containsExactlyInAnyOrder(7L, 8L);
        Order seller7 = saved.stream().filter(o -> o.getSellerId().equals(7L)).findFirst().orElseThrow();
        assertThat(seller7.getItems()).hasSize(2);
        assertThat(seller7.getTotalAmount()).isEqualByComparingTo("110.00");

        verify(outboxEventWriter, times(2)).write(eq("OrderCreatedEvent"), eq("order.created"), anyString(), any());
        verify(cartApiClient, times(1)).clearCart();
        assertThat(result).hasSize(2);
    }

    // ------------------------------------------------------------------ coupon

    @Test
    void create_blankCouponCode_isIgnored() {
        stubCart(TestData.cart("   ", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();

        service.create(request(null), userWithDefaultAddress());

        verifyNoInteractions(couponApiClient);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponCode()).isNull();
    }

    @Test
    @DisplayName("create redeems the coupon once for the whole checkout and subtracts each seller's allocation")
    void create_validCoupon_appliesAllocationsPerSeller() {
        stubCart(TestData.cart("SAVE10",
                TestData.cartItem(7L, "P1", 1, "800.00", "1000.00", "800.00"),
                TestData.cartItem(8L, "P2", 1, "200.00", "200.00", "200.00")));
        stubSaveAssignsIds();
        when(couponApiClient.redeem(any(RedeemRequest.class)))
                .thenReturn(ApiResponse.success(validResult(7L, "80.00", 8L, "20.00")));

        service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<RedeemRequest> redeemCaptor = ArgumentCaptor.forClass(RedeemRequest.class);
        verify(couponApiClient, times(1)).redeem(redeemCaptor.capture());
        RedeemRequest redeem = redeemCaptor.getValue();
        assertThat(redeem.code()).isEqualTo("SAVE10");
        assertThat(redeem.checkoutRef()).isNotBlank();
        assertThat(redeem.lines()).containsExactlyInAnyOrder(
                new CouponLine(7L, new BigDecimal("800.00")),
                new CouponLine(8L, new BigDecimal("200.00")));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(captor.capture());
        Order seller7 = captor.getAllValues().stream().filter(o -> o.getSellerId().equals(7L)).findFirst().orElseThrow();
        Order seller8 = captor.getAllValues().stream().filter(o -> o.getSellerId().equals(8L)).findFirst().orElseThrow();
        assertThat(seller7.getCouponCode()).isEqualTo("SAVE10");
        assertThat(seller7.getCouponDiscount()).isEqualByComparingTo("80.00");
        assertThat(seller7.getTotalAmount()).isEqualByComparingTo("720.00");
        assertThat(seller8.getCouponDiscount()).isEqualByComparingTo("20.00");
        assertThat(seller8.getTotalAmount()).isEqualByComparingTo("180.00");
        assertThat(seller7.getCheckoutRef()).isEqualTo(redeem.checkoutRef());
        assertThat(seller8.getCheckoutRef()).isEqualTo(redeem.checkoutRef());
        verify(couponApiClient, never()).release(any());
    }

    @Test
    void create_couponAllocationAboveSellerTotal_isCappedAtSellerTotal() {
        stubCart(TestData.cart("BIG", TestData.cartItem(7L, "P1", 1, "800.00", "800.00", "800.00")));
        stubSaveAssignsIds();
        when(couponApiClient.redeem(any(RedeemRequest.class)))
                .thenReturn(ApiResponse.success(validResult(7L, "900.00", null, null)));

        service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponDiscount()).isEqualByComparingTo("800.00");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void create_validCouponWithoutAllocations_givesNoDiscount() {
        stubCart(TestData.cart("NOALLOC", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();
        CouponResult result = new CouponResult();
        result.setValid(true);
        when(couponApiClient.redeem(any(RedeemRequest.class))).thenReturn(ApiResponse.success(result));

        service.create(request(null), userWithDefaultAddress());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponDiscount()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void create_couponInvalidWithMessage_badRequestAndNothingSaved() {
        stubCart(TestData.cart("OLD", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        CouponResult result = new CouponResult();
        result.setValid(false);
        result.setMessage("Coupon expired.");
        when(couponApiClient.redeem(any(RedeemRequest.class))).thenReturn(ApiResponse.success(result));

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("OLD")
                .hasMessageContaining("Coupon expired.");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
        verify(cartApiClient, never()).clearCart();
    }

    @Test
    void create_couponInvalidWithoutMessage_usesDefaultReason() {
        stubCart(TestData.cart("OLD", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        CouponResult result = new CouponResult();
        result.setValid(false);
        when(couponApiClient.redeem(any(RedeemRequest.class))).thenReturn(ApiResponse.success(result));

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("it is not valid");
    }

    @Test
    void create_couponResultNull_badRequest() {
        stubCart(TestData.cart("OLD", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        when(couponApiClient.redeem(any(RedeemRequest.class))).thenReturn(ApiResponse.success(null));

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("it is not valid");
    }

    @Test
    @DisplayName("a failing coupon-service call becomes a 503 and the possibly-recorded use is released")
    void create_couponServiceDown_serviceUnavailableAndReleased() {
        stubCart(TestData.cart("SAVE10", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        RuntimeException boom = new RuntimeException("connection refused");
        when(couponApiClient.redeem(any(RedeemRequest.class))).thenThrow(boom);

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasCause(boom);

        ArgumentCaptor<RedeemRequest> redeem = ArgumentCaptor.forClass(RedeemRequest.class);
        verify(couponApiClient).redeem(redeem.capture());
        ArgumentCaptor<ReleaseRequest> release = ArgumentCaptor.forClass(ReleaseRequest.class);
        verify(couponApiClient).release(release.capture());
        assertThat(release.getValue().checkoutRef()).isEqualTo(redeem.getValue().checkoutRef());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_couponServiceDownAndReleaseAlsoFails_stillServiceUnavailable() {
        stubCart(TestData.cart("SAVE10", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        when(couponApiClient.redeem(any(RedeemRequest.class))).thenThrow(new RuntimeException("down"));
        when(couponApiClient.release(any(ReleaseRequest.class))).thenThrow(new RuntimeException("still down"));

        assertThatThrownBy(() -> service.create(request(null), userWithDefaultAddress()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    // ------------------------------------------------------------------ transaction synchronization

    @Test
    void create_cartClearFails_orderStillReturned() {
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();
        doThrow(new RuntimeException("cart down")).when(cartApiClient).clearCart();

        List<OrderResponse> result = service.create(request(null), userWithDefaultAddress());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("inside a transaction the cart is cleared only after commit")
    void create_inTransaction_clearsCartAfterCommit() {
        stubCart(TestData.cart(null, TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();
        TransactionSynchronizationManager.initSynchronization();

        service.create(request(null), userWithDefaultAddress());

        verify(cartApiClient, never()).clearCart();
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(cartApiClient, times(1)).clearCart();
    }

    @Test
    void create_inTransactionRolledBack_releasesCoupon() {
        stubCart(TestData.cart("SAVE10", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();
        when(couponApiClient.redeem(any(RedeemRequest.class)))
                .thenReturn(ApiResponse.success(validResult(7L, "10.00", null, null)));
        TransactionSynchronizationManager.initSynchronization();

        service.create(request(null), userWithDefaultAddress());

        verify(couponApiClient, never()).release(any());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        ArgumentCaptor<ReleaseRequest> release = ArgumentCaptor.forClass(ReleaseRequest.class);
        verify(couponApiClient, times(1)).release(release.capture());
        assertThat(release.getValue().checkoutRef()).isNotBlank();
    }

    @Test
    void create_inTransactionCommitted_doesNotReleaseCoupon() {
        stubCart(TestData.cart("SAVE10", TestData.cartItem(7L, "P1", 1, "100.00", "100.00", "100.00")));
        stubSaveAssignsIds();
        when(couponApiClient.redeem(any(RedeemRequest.class)))
                .thenReturn(ApiResponse.success(validResult(7L, "10.00", null, null)));
        TransactionSynchronizationManager.initSynchronization();

        service.create(request(null), userWithDefaultAddress());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(couponApiClient, never()).release(any());
    }
}
