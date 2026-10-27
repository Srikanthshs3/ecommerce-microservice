package org.praskanth.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailsTest {

    @ParameterizedTest
    @CsvSource({
            "'  Alice@Example.COM ', alice@example.com",
            "bob@example.com, bob@example.com",
            "'\tMixed@Case.Org\t', mixed@case.org"
    })
    void normalize_trimsAndLowercases(String input, String expected) {
        assertThat(Emails.normalize(input)).isEqualTo(expected);
    }

    @Test
    void normalize_null_returnsNull() {
        assertThat(Emails.normalize(null)).isNull();
    }

    @Test
    void normalize_blank_returnsEmpty() {
        assertThat(Emails.normalize("   ")).isEmpty();
    }
}
