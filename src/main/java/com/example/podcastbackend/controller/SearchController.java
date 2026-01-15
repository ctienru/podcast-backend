package com.example.podcastbackend.controller;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.service.SearchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/shows")
    public ShowSearchResponse searchShows(
            @RequestBody ShowSearchRequest request
    ) {
        return searchService.searchShows(request);
    }

    @PostMapping("/episodes")
    public EpisodeSearchResponse searchEpisodes(
            @RequestBody EpisodeSearchRequest request
    ) {
        return searchService.searchEpisodes(request);
    }
}