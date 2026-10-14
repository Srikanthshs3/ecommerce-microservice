package org.praskanth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.praskanth.crypto.EncryptedStringConverter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "accountNumber")
public class BankDetails {

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", length = 255)
    private String accountNumber;

    private String accountHolderName;
    private String ifscCode;
}
