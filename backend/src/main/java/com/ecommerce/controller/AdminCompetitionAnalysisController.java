package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.CompetitionArtifactsTriggerDTO;
import com.ecommerce.service.CompetitionWorkbenchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analysis/competition")
public class AdminCompetitionAnalysisController {

    @Autowired
    private CompetitionWorkbenchService competitionWorkbenchService;

    @GetMapping("/workbench")
    public Result<?> workbench(@RequestParam(required = false) String snapshotDate,
                               @RequestParam(defaultValue = "7") int lookbackDays) {
        return Result.success(competitionWorkbenchService.getWorkbench(snapshotDate, lookbackDays));
    }

    @PostMapping({"/workbench/trigger", "/tasks/trigger"})
    public Result<?> triggerCompetitionArtifacts(@RequestBody CompetitionArtifactsTriggerDTO request) {
        return Result.success(competitionWorkbenchService.triggerCompetitionArtifacts(request));
    }
}
