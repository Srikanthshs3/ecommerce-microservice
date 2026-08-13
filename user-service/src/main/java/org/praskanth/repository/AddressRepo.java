package org.praskanth.repository;

import org.praskanth.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepo extends JpaRepository<Address , Long> {
    // Fetch all addresses for a specific customer
    List<Address> findByCustomerProfileUserId(Long userId);

    // Fetch a specific address, ensuring it belongs to the requesting customer
    Optional<Address> findByIdAndCustomerProfileUserId(Long id, Long userId);
}
