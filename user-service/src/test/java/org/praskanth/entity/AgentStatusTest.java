package org.praskanth.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.praskanth.entity.DeliveryAgentProfile.AgentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, APPROVED", "PENDING, REJECTED",
            "APPROVED, SUSPENDED",
            "SUSPENDED, APPROVED",
            "REJECTED, APPROVED", "REJECTED, PENDING"
    })
    void canTransitionTo_allowed(AgentStatus from, AgentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, SUSPENDED", "PENDING, PENDING",
            "APPROVED, REJECTED", "APPROVED, PENDING", "APPROVED, APPROVED",
            "SUSPENDED, REJECTED", "SUSPENDED, PENDING",
            "REJECTED, SUSPENDED", "REJECTED, REJECTED"
    })
    void canTransitionTo_forbidden(AgentStatus from, AgentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void builderDefaults_pendingAndOffDuty() {
        DeliveryAgentProfile p = DeliveryAgentProfile.builder().build();

        assertThat(p.getStatus()).isEqualTo(AgentStatus.PENDING);
        assertThat(p.isOnDuty()).isFalse();
    }
}
