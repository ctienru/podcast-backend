package com.example.podcastbackend.response;

import java.util.List;

public record SuggestResponseData(
        List<ShowSuggestItem> shows,
        List<EpisodeSuggestItem> episodes
) {}
