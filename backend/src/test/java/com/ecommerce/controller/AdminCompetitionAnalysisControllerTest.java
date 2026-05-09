package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.CompetitionWorkbenchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCompetitionAnalysisControllerTest {

    @Mock
    private CompetitionWorkbenchService competitionWorkbenchService;

    private AdminCompetitionAnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminCompetitionAnalysisController();
        ReflectionTestUtils.setField(controller, "competitionWorkbenchService", competitionWorkbenchService);
    }

    @Test
    void workbenchShouldWrapServiceResponse() {
        Map<String, Object> payload = Collections.<String, Object>singletonMap("available", Boolean.TRUE);
        when(competitionWorkbenchService.getWorkbench("2026-03-27", 5)).thenReturn(payload);

        Result<?> result = controller.workbench("2026-03-27", 5);

        assertEquals(200, result.getCode());
        assertSame(payload, result.getData());
        verify(competitionWorkbenchService).getWorkbench("2026-03-27", 5);
    }
}
