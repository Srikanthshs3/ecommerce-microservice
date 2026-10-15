package org.praskanth.mapper;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.UserResponse;
import org.praskanth.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private static final List<String> ROLE_PRIORITY =
            List.of("ADMIN", "SELLER", "SUPPORT_AGENT", "DELIVERY_PARTNER", "CUSTOMER");

    private final AddressMapper addressMapper;

    public static Set<String> roleNames(User user) {
        return user.getRole().stream().map(r -> r.getName().name()).collect(Collectors.toSet());
    }

    /** Highest-privilege role; used where a single role string is still expected. */
    public static String primaryRole(Collection<String> roles) {
        return ROLE_PRIORITY.stream().filter(roles::contains).findFirst().orElse("UNKNOWN");
    }

    /** Must be called inside a transaction when includeAddresses is true (lazy collection). */
    public UserResponse toResponse(User user, boolean includeAddresses) {
        var builder = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .role(roleNames(user))
                .emailVerified(user.isEmailVerified())
                .accountNonLocked(user.isAccountNonLocked())
                .createdAt(user.getCreatedAt());
        if (includeAddresses && user.getCustomerProfile() != null) {
            builder.addresses(addressMapper.toResponses(user.getCustomerProfile().getAddresses()));
        }
        return builder.build();
    }
}
