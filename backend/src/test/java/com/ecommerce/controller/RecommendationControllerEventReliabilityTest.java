package com.ecommerce.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.RecommendationAsyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerEventReliabilityTest {

    @Mock
    private ModuleSwitchService moduleSwitchService;

    @Mock
    private RecommendationAsyncService recommendationAsyncService;

    private RecommendationController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new RecommendationController();
        ReflectionTestUtils.setField(controller, "moduleSwitchService", moduleSwitchService);
        ReflectionTestUtils.setField(controller, "recommendationAsyncService", recommendationAsyncService);
        request = new MockHttpServletRequest();
        request.setAttribute("userId", 99L);
        lenient().when(moduleSwitchService.isEnabled("recommendation")).thenReturn(true);
    }

    @Test
    void batchEventsSkipInvalidRowsAndKeepOrderRefundAttributionEvents() {
        RecommendationEventDTO click = event("CLICK", 11L, null);
        RecommendationEventDTO order = event(Constants.RecommendationEventType.ORDER, null, 7001L);
        RecommendationEventDTO refund = event(Constants.RecommendationEventType.REFUND, null, 7002L);
        RecommendationEventDTO invalidClickWithoutProduct = event(Constants.RecommendationEventType.CLICK, null, null);
        RecommendationEventDTO invalidType = event("unknown", 12L, null);
        RecommendationEventDTO missingType = event(null, 13L, null);

        Result<?> result = controller.recordRecommendationEvents(Arrays.asList(
                click,
                order,
                refund,
                invalidClickWithoutProduct,
                invalidType,
                missingType), request);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.getData();
        assertEquals(3, payload.get("accepted"));
        assertEquals(6, payload.get("received"));
        assertEquals(Constants.RecommendationEventType.CLICK, click.getEventType());
        verify(recommendationAsyncService, times(3)).recordRecommendationEventAsync(eq(99L), any(RecommendationEventDTO.class));
    }

    @Test
    void singleClickWithoutProductIsRejectedButOrderOnlyEventIsAccepted() {
        assertThrows(BusinessException.class,
                () -> controller.recordRecommendationEvent(event(Constants.RecommendationEventType.CLICK, null, null), request));

        RecommendationEventDTO orderOnly = event(Constants.RecommendationEventType.ORDER, null, 8001L);
        Result<?> result = controller.recordRecommendationEvent(orderOnly, request);

        assertEquals(200, result.getCode());
        verify(recommendationAsyncService).recordRecommendationEventAsync(99L, orderOnly);
    }

    @Test
    void disabledRecommendationModuleAcceptsWithoutAsyncSideEffects() {
        when(moduleSwitchService.isEnabled("recommendation")).thenReturn(false);

        Result<?> result = controller.recordRecommendationEvents(List.of(event(Constants.RecommendationEventType.EXPOSURE, 1L, null)), request);

        assertEquals(200, result.getCode());
        verify(recommendationAsyncService, times(0)).recordRecommendationEventAsync(any(), any());
    }

    private RecommendationEventDTO event(String eventType, Long productId, Long orderId) {
        RecommendationEventDTO dto = new RecommendationEventDTO();
        dto.setEventType(eventType);
        dto.setProductId(productId);
        dto.setOrderId(orderId);
        return dto;
    }
}
