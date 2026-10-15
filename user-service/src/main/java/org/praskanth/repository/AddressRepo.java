package org.praskanth.repository;

import org.praskanth.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepo extends JpaRepository<Address , Long> {
    // Fetch all addresses for a specific customer
    //List<Address> findByCustomerProfileUserId(Long userId);

    // Fetch a specific address, ensuring it belongs to the requesting customer
    //Optional<Address> findByIdAndCustomerProfileUserId(Long id, Long userId);

    @Query("SELECT a FROM Address a " +
            "LEFT JOIN a.customerProfile cp " +
            "LEFT JOIN a.sellerProfile sp " +
            "WHERE cp.userId = :userId OR sp.userId = :userId")
    List<Address> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT a FROM Address a " +
            "LEFT JOIN a.customerProfile cp " +
            "LEFT JOIN a.sellerProfile sp " +
            "WHERE a.id = :addressId AND (cp.userId = :userId OR sp.userId = :userId)")
    Optional<Address> findByAddressIdAndUserId(@Param("addressId") Long addressId, @Param("userId") Long userId);
}
