package org.praskanth.repository;

import jakarta.persistence.LockModeType;
import org.praskanth.entity.CheckoutPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckoutPaymentRepository extends JpaRepository<CheckoutPayment, Long> {

    Optional<CheckoutPayment> findByCheckoutRef(String checkoutRef);

    /**
     * SELECT ... FOR UPDATE. "Customer clicks Pay twice", "browser confirm and webhook arrive together" and
     * "order expires while the customer pays" all change the same row; the lock makes them take turns, so exactly one
     * of them performs the transition and the others see the new state and do nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CheckoutPayment p where p.checkoutRef = :ref")
    Optional<CheckoutPayment> lockByCheckoutRef(@Param("ref") String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CheckoutPayment p where p.id = :id")
    Optional<CheckoutPayment> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CheckoutPayment p where p.gatewayOrderId = :gatewayOrderId")
    Optional<CheckoutPayment> lockByGatewayOrderId(@Param("gatewayOrderId") String gatewayOrderId);
}
