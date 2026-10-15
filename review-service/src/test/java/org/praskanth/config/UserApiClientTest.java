package org.praskanth.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserApiClientTest {

    @Test
    void showsFirstNameAndLastInitial() {
        assertEquals("Srikanth P.", UserApiClient.maskName("Srikanth Prasad", "x@y.com"));
        assertEquals("Anita K.", UserApiClient.maskName("  Anita   Mary Kumar ", "x@y.com"));
    }

    @Test
    void singleNameIsKeptAsIs() {
        assertEquals("Madonna", UserApiClient.maskName("Madonna", "x@y.com"));
    }

    @Test
    void fallsBackToMaskedEmail() {
        assertEquals("pra***", UserApiClient.maskName(null, "praskanth@gmail.com"));
        assertEquals("Customer", UserApiClient.maskName("", ""));
    }
}
