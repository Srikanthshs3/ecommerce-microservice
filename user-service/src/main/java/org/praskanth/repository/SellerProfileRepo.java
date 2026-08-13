package org.praskanth.repository;

import org.praskanth.entity.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Range;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepo extends JpaRepository<SellerProfile, Long> {

    Page<SellerProfile> findByKycStatus(SellerProfile.KycStatus kycStatus, Pageable pageable);
}
