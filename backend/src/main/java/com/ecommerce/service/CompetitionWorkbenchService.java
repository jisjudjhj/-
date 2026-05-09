package com.ecommerce.service;

import com.ecommerce.dto.CompetitionArtifactsTriggerDTO;

import java.util.Map;

public interface CompetitionWorkbenchService {

    Map<String, Object> getWorkbench(String snapshotDate, int lookbackDays);

    Map<String, Object> triggerCompetitionArtifacts(CompetitionArtifactsTriggerDTO request);
}
