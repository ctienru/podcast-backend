package com.example.podcastbackend.request;

public class RankingsRequest {

    private final String region;
    private final String type;
    private final int limit;

    public RankingsRequest(String region, String type, int limit) {
        this.region = region;
        this.type = type;
        this.limit = limit;
    }

    public String getRegion() {
        return region;
    }

    public String getType() {
        return type;
    }

    public int getLimit() {
        return limit;
    }
}
