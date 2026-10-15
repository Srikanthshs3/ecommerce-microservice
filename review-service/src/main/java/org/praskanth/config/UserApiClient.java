package org.praskanth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.RemoteResponse;
import org.praskanth.dto.RemoteUser;
import org.praskanth.security.gateway.GatewayUser;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserApiClient {

    private final RestTemplate restTemplate;

    /**
     * Public name shown next to a review, privacy-friendly: "Srikanth P." (first name + last initial).
     * Never fails: if user-service is unavailable the e-mail's local part is used ("srikanth***").
     */
    public String displayName(GatewayUser caller) {
        String name = null;
        try {
            RemoteResponse<RemoteUser> body = restTemplate.exchange("http://user-service/api/v1/user/profile",
                    HttpMethod.GET, null, new ParameterizedTypeReference<RemoteResponse<RemoteUser>>() {}).getBody();
            if (body != null && body.getData() != null) {
                name = body.getData().getName();
            }
        } catch (Exception e) {
            log.warn("Could not load profile of user {} for review display name: {}", caller.id(), e.getMessage());
        }
        return maskName(name, caller.email());
    }

    static String maskName(String fullName, String email) {
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return truncate(parts[0]);
            }
            return truncate(parts[0]) + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
        }
        String local = email == null ? "" : email.split("@")[0];
        if (local.isBlank()) {
            return "Customer";
        }
        return truncate(local.length() > 3 ? local.substring(0, 3) : local) + "***";
    }

    private static String truncate(String s) {
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
