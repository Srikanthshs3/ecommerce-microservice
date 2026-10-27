package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.ServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * Asks user-service "who is this delivery partner?" (approved? on duty? which pincodes?).
 * The call is made while serving the agent's own request, so the load-balanced RestTemplate forwards the caller's
 * gateway identity and user-service answers for THAT user: an agent can never ask about someone else.
 * Nothing is cached on purpose: a suspension must stop pickups immediately.
 */
@Component
@RequiredArgsConstructor
public class AgentDirectory {

    public record AgentInfo(Long id, String status, boolean onDuty, List<String> pincodes) {}

    private final AgentProfileClient agentProfileClient; // the protected HTTP call (circuit breaker + bulkhead + retry)

    public AgentInfo me() {
        try {
            return agentProfileClient.fetchMe();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ForbiddenOperationException("Finish your delivery partner application first (Register > Delivery partner).");
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // timeout, connection refused, 5xx, open circuit or full bulkhead (see AgentProfileClient)
            throw new ServiceUnavailableException("We couldn't load the delivery partner details right now. Please try again shortly.", ex);
        }
    }

    /** Approved agents only; everyone else gets a message that says what to do. */
    public AgentInfo requireApproved() {
        AgentInfo info = me();
        if (!"APPROVED".equals(info.status())) {
            throw new ForbiddenOperationException("Your delivery partner profile is " + info.status() + ". An admin must approve it first.");
        }
        return info;
    }

    public AgentInfo requireOnDuty() {
        AgentInfo info = requireApproved();
        if (!info.onDuty()) {
            throw new ConflictException("You are off duty. Switch on duty first.");
        }
        return info;
    }
}
