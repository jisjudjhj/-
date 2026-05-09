package com.ecommerce.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.recommendation.ABTestFramework;
import com.ecommerce.service.ModuleSwitchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerAbTestSecurityTest {

    @Mock
    private ModuleSwitchService moduleSwitchService;

    @Mock
    private ABTestFramework abTestFramework;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController();
        ReflectionTestUtils.setField(controller, "moduleSwitchService", moduleSwitchService);
        ReflectionTestUtils.setField(controller, "abTestFramework", abTestFramework);
    }

    @Test
    void abtestReportShouldRejectNonAdminRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("role", "merchant");

        assertThrows(BusinessException.class, () -> controller.abtestReport(request));
        verify(moduleSwitchService, never()).requireEnabled("ab-test");
    }

    @Test
    void abtestResetShouldRejectMissingRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(BusinessException.class, () -> controller.abtestReset(request));
        verify(abTestFramework, never()).resetMetrics();
    }

    @Test
    void abtestReportShouldReturnMetricsForAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("role", "ADMIN");
        Map<String, Object> report = Collections.singletonMap("A", Collections.singletonMap("totalExposures", 3L));
        when(moduleSwitchService.isEnabled("ab-test")).thenReturn(true);
        when(abTestFramework.getReport()).thenReturn(report);

        Result<?> result = controller.abtestReport(request);

        assertEquals(200, result.getCode());
        assertSame(report, result.getData());
        verify(moduleSwitchService).requireEnabled("ab-test");
    }

    @Test
    void abtestResetShouldInvokeResetForAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("role", "admin");
        when(moduleSwitchService.isEnabled("ab-test")).thenReturn(true);

        Result<?> result = controller.abtestReset(request);

        assertEquals(200, result.getCode());
        verify(moduleSwitchService).requireEnabled("ab-test");
        verify(abTestFramework).resetMetrics();
    }
}
