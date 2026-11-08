package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.DeliveryAgentDtos.AgentRequest;
import org.praskanth.dto.DeliveryAgentDtos.AgentResponse;
import org.praskanth.dto.DeliveryAgentDtos.DutyRequest;
import org.praskanth.dto.DeliveryAgentDtos.PartnerRegisterRequest;
import org.praskanth.dto.DeliveryAgentDtos.StatusRequest;
import org.praskanth.entity.DeliveryAgentProfile.AgentStatus;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.DeliveryAgentService;
import org.praskanth.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Delivery partner onboarding.
 *   public:  POST /register (after the two OTP calls of /api/v1/auth/register/*)
 *   a partner: POST /apply (re-apply after a rejection), GET|PUT /me, PUT /me/duty (the service refuses unless APPROVED)
 *   ADMIN / SUPPORT_AGENT: list + detail;  ADMIN only: PUT /{id}/status (approve / reject / suspend)
 * shipping-service also calls GET /me (with the caller's identity) to learn the agent's status, duty flag and pincodes.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/delivery-agents")
public class DeliveryAgentController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "status", "city", "createdAt");

    private final DeliveryAgentService service;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AgentResponse>> register(@Valid @RequestBody PartnerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Registration submitted. You can start delivering once an admin approves you.", service.register(request)));
    }

    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AgentResponse>> apply(@AuthenticationPrincipal GatewayUser me, @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Application submitted. An admin will review it.", service.apply(me.id(), request)));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AgentResponse>> me(@AuthenticationPrincipal GatewayUser me) {
        return ResponseEntity.ok(ApiResponse.success(service.me(me.id())));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AgentResponse>> updateMe(@AuthenticationPrincipal GatewayUser me, @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated", service.updateMe(me.id(), request)));
    }

    @PutMapping("/me/duty")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AgentResponse>> duty(@AuthenticationPrincipal GatewayUser me, @Valid @RequestBody DutyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(request.onDuty() ? "You are on duty" : "You are off duty",
                service.setDuty(me.id(), request.onDuty())));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<Page<AgentResponse>>> list(@RequestParam(required = false) AgentStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequests.of(page, size, "createdAt", "desc", SORT_FIELDS, "id");
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<AgentResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> review(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
                                                             @AuthenticationPrincipal GatewayUser admin) {
        return ResponseEntity.ok(ApiResponse.success("Status updated", service.review(id, request.status(), request.reason(), admin.id())));
    }
}
