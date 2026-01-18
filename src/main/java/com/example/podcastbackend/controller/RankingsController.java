package com.example.podcastbackend.controller;

import com.example.podcastbackend.request.RankingsRequest;
import com.example.podcastbackend.response.RankingsResponse;
import com.example.podcastbackend.service.RankingsService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rankings")
@Validated
@Tag(name = "Rankings", description = "Podcast and episode rankings from Apple Charts")
public class RankingsController {

    private final RankingsService rankingsService;

    public RankingsController(RankingsService rankingsService) {
        this.rankingsService = rankingsService;
    }

    @GetMapping
    @RateLimiter(name = "rankingsApi")
    @Operation(summary = "Get rankings", description = "Retrieve podcast or episode rankings from Apple Charts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rankings retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "Apple Charts service unavailable")
    })
    public RankingsResponse getRankings(
            @Parameter(description = "Country code", example = "tw")
            @RequestParam(defaultValue = "tw")
            @Pattern(regexp = "^(tw|us)$", message = "Country must be 'tw' or 'us'")
            String country,

            @Parameter(description = "Chart type", example = "podcast")
            @RequestParam(defaultValue = "podcast")
            @Pattern(regexp = "^(podcast|episode)$", message = "Type must be 'podcast' or 'episode'")
            String type,

            @Parameter(description = "Number of results to return", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit must not exceed 100")
            Integer limit
    ) {
        RankingsRequest request = new RankingsRequest(country, type, limit);
        return rankingsService.getRankings(request);
    }
}
