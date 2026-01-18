package com.example.podcastbackend.controller;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.service.SearchService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Podcast and episode search APIs")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/shows")
    @RateLimiter(name = "searchApi")
    @Operation(summary = "Search podcasts", description = "Search for podcasts by keyword with pagination support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ShowSearchResponse searchShows(
            @Valid @RequestBody ShowSearchRequest request
    ) {
        return searchService.searchShows(request);
    }

    @PostMapping("/episodes")
    @RateLimiter(name = "searchApi")
    @Operation(summary = "Search episodes", description = "Search for podcast episodes by keyword with pagination and sorting")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public EpisodeSearchResponse searchEpisodes(
            @Valid @RequestBody EpisodeSearchRequest request
    ) {
        return searchService.searchEpisodes(request);
    }
}