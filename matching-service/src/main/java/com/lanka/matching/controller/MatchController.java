package com.lanka.matching.controller;

import com.lanka.matching.dto.BatchMatchRequest;
import com.lanka.matching.dto.BatchMatchResponse;
import com.lanka.matching.dto.MatchRequest;
import com.lanka.matching.dto.MatchResponse;
import com.lanka.matching.model.MatchResult;
import com.lanka.matching.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/matches")
@Tag(name = "Matching", description = "Rule-based worker/job skill matching (no ML model)")
public class MatchController {
    private final MatchingService service;

    public MatchController(MatchingService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Score one job for one worker and store the result as match history")
    MatchResponse match(@Valid @RequestBody MatchRequest request) {
        return service.match(request);
    }

    @PostMapping("/batch")
    @Operation(summary = "Score the whole job feed for one worker in a single call (not persisted)")
    BatchMatchResponse matchBatch(@Valid @RequestBody BatchMatchRequest request) {
        List<MatchResponse> results = service.matchAll(request.workerDistrict(), request.workerSkills(), request.jobs());
        return new BatchMatchResponse(results.size(), results);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Stored match history for one job")
    List<MatchResult> history(@PathVariable Long jobId) {
        return service.getByJobId(jobId);
    }
}
