package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BusinessDetails {
    private String businessName;
    private String businessAddress;
    private String businessPhone;
    private String businessEmail;
    private String logo;
    private String banner;
}
