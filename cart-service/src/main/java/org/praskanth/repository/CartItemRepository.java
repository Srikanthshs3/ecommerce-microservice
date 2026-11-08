package org.praskanth.repository;

import org.praskanth.modal.Cart;
import org.praskanth.modal.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem,Long> {

    CartItem findByCartAndProductIdAndSize(Cart cart, String productId, String size);

    /** The cart line of one variant of a product (a different colour/size is a different line). */
    CartItem findByCartAndProductIdAndVariantId(Cart cart, String productId, String variantId);
}
