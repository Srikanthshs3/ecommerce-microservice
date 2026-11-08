package org.praskanth.repository;

import org.praskanth.entity.DeliveryAgentProfile;
import org.praskanth.entity.DeliveryAgentProfile.AgentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAgentRepo extends JpaRepository<DeliveryAgentProfile, Long> {
    Page<DeliveryAgentProfile> findByStatus(AgentStatus status, Pageable pageable);
}
