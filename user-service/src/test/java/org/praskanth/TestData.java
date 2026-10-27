package org.praskanth;

import org.praskanth.dto.DeliveryAgentDtos.AgentRequest;
import org.praskanth.dto.SellerProfileRequest;
import org.praskanth.entity.Role;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;

import java.util.HashSet;
import java.util.Set;

/** Small shared builders for the user-service tests. */
public final class TestData {

    public static final String GSTIN = "29ABCDE1234F1Z5";
    public static final String PAN = "ABCDE1234F";

    private TestData() {
    }

    public static Role role(UserRole name) {
        return Role.builder().id((long) (name.ordinal() + 1)).name(name).build();
    }

    public static User user(Long id, String email, UserRole... roles) {
        Set<Role> set = new HashSet<>();
        for (UserRole r : roles) {
            set.add(role(r));
        }
        return User.builder()
                .id(id)
                .email(email)
                .name("Test User")
                .password("encoded-pw")
                .role(set)
                .accountNonLocked(true)
                .build();
    }

    public static SellerProfileRequest sellerProfileRequest() {
        return new SellerProfileRequest(
                GSTIN, PAN,
                new SellerProfileRequest.Business("My Store", "My Store Pvt Ltd", "12 Market Road",
                        "9876543210", "Biz@Example.com", null, null),
                new SellerProfileRequest.Bank("Holder Name", "123456789012", "HDFC0001234"));
    }

    public static AgentRequest agentRequest() {
        return new AgentRequest("9876543210", "bike", "ka01ab1234", "aadhaar", "abcd123456", "Bengaluru",
                "560001, 560002 560001");
    }
}
