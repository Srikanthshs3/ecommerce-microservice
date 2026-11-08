package org.praskanth.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.service.OrderService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpiryJobTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderExpiryJob job;

    @Test
    void expireUnpaidOrders_cancelsEveryExpiredOrder() {
        when(orderService.findExpiredUnpaidOrderIds()).thenReturn(List.of(1L, 2L));

        job.expireUnpaidOrders();

        verify(orderService).markCancelled(1L, "Payment was not completed in time");
        verify(orderService).markCancelled(2L, "Payment was not completed in time");
    }

    @Test
    void expireUnpaidOrders_nothingExpired_cancelsNothing() {
        when(orderService.findExpiredUnpaidOrderIds()).thenReturn(List.of());

        job.expireUnpaidOrders();

        verify(orderService, never()).markCancelled(anyLong(), anyString());
    }

    @Test
    void expireUnpaidOrders_oneFailure_doesNotBlockTheOthers() {
        when(orderService.findExpiredUnpaidOrderIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("db hiccup")).when(orderService).markCancelled(2L, "Payment was not completed in time");

        job.expireUnpaidOrders();

        verify(orderService).markCancelled(1L, "Payment was not completed in time");
        verify(orderService).markCancelled(3L, "Payment was not completed in time");
    }
}
