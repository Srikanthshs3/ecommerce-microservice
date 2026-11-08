package org.praskanth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock RefreshTokenService refreshTokenService;
    @InjectMocks RefreshTokenCleanupJob job;

    @Test
    @DisplayName("the scheduled job purges expired tokens through the service")
    void purgeExpiredTokens_callsService() {
        when(refreshTokenService.purgeExpired()).thenReturn(5);

        job.purgeExpiredTokens();

        verify(refreshTokenService).purgeExpired();
    }
}
