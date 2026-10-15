package org.praskanth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.praskanth.enums.UserRole;

@Entity
@Table(name = "user_role")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false, unique = true)
    private UserRole name;

/*    public enum UserRole {
        ADMIN, CUSTOMER, SELLER, DELIVERY_PARTNER, SUPPORT_AGENT;
    }

    @JsonCreator
    public static Role fromString(@JsonProperty("name") String name) {
        Role role = new Role();
        role.setName(UserRole.valueOf(name));
        return role;
    }*/
}
