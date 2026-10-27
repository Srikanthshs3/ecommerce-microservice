package org.praskanth.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FallbacksTest {

    @Test
    @DisplayName("4xx from the other service passes through unchanged")
    void translate_httpClientError_returnsSameException() {
        // Given
        HttpClientErrorException failure = new HttpClientErrorException(HttpStatus.NOT_FOUND);
        // When
        RuntimeException result = Fallbacks.translate("product catalogue", failure);
        // Then
        assertThat(result).isSameAs(failure);
    }

    @Test
    @DisplayName("our own business exceptions pass through unchanged")
    void translate_businessException_returnsSameException() {
        NotFoundException notFound = new NotFoundException("nope");
        BadRequestException badRequest = new BadRequestException("bad");
        ServiceUnavailableException unavailable = new ServiceUnavailableException("already 503");

        assertThat(Fallbacks.translate("x", notFound)).isSameAs(notFound);
        assertThat(Fallbacks.translate("x", badRequest)).isSameAs(badRequest);
        assertThat(Fallbacks.translate("x", unavailable)).isSameAs(unavailable);
    }

    @Test
    @DisplayName("5xx from the other service becomes ServiceUnavailableException with the cause attached")
    void translate_serverError_becomesServiceUnavailable() {
        // Given
        HttpServerErrorException failure = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        // When
        RuntimeException result = Fallbacks.translate("inventory service", failure);
        // Then
        assertThat(result).isInstanceOf(ServiceUnavailableException.class);
        assertThat(result.getCause()).isSameAs(failure);
        assertThat(result.getMessage()).contains("inventory service").contains("couldn't reach");
    }

    @Test
    @DisplayName("checked I/O failures are translated too")
    void translate_checkedException_becomesServiceUnavailable() {
        IOException failure = new IOException("connection refused");

        RuntimeException result = Fallbacks.translate("payment gateway", failure);

        assertThat(result).isInstanceOf(ServiceUnavailableException.class);
        assertThat(result.getCause()).isSameAs(failure);
    }

    @Test
    @DisplayName("a plain RuntimeException outside the business package is an outage")
    void translate_plainRuntimeException_becomesServiceUnavailable() {
        RuntimeException failure = new RuntimeException("timeout");

        assertThat(Fallbacks.translate("x", failure)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("isCallerProblem recognises 4xx and business exceptions only")
    void isCallerProblem_classification() {
        assertThat(Fallbacks.isCallerProblem(new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isTrue();
        assertThat(Fallbacks.isCallerProblem(new NotFoundException("x"))).isTrue();
        assertThat(Fallbacks.isCallerProblem(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))).isFalse();
        assertThat(Fallbacks.isCallerProblem(new IllegalStateException("x"))).isFalse();
        assertThat(Fallbacks.isCallerProblem(new IOException("x"))).isFalse();
    }
}
