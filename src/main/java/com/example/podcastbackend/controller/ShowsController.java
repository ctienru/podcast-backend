package com.example.podcastbackend.controller;

import com.example.podcastbackend.response.ShowBatchResponse;
import com.example.podcastbackend.service.ShowsService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@Tag(name = "Shows", description = "Podcast show information APIs")
public class ShowsController {

    private final ShowsService showsService;

    public ShowsController(ShowsService showsService) {
        this.showsService = showsService;
    }

    @GetMapping("/batch")
    @RateLimiter(name = "searchApi")
    @Operation(
            summary = "Batch get show details",
            description = "Fetch detailed information for multiple shows by their IDs"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shows fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ShowBatchResponse batchGetShows(
            @Parameter(description = "Comma-separated list of show IDs", example = "show:apple:123,show:apple:456")
            @RequestParam("ids") List<String> showIds
    ) {
        // Limit to 100 shows per request
        if (showIds.size() > 100) {
            return ShowBatchResponse.error("INVALID_REQUEST", "Maximum 100 show IDs per request");
        }

        return showsService.batchGetShows(showIds);
    }
}
