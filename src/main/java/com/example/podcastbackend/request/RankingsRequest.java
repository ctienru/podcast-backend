package com.example.podcastbackend.request;

public class RankingsRequest {

    private final String country;
    private final String type;
    private final int limit;

    public RankingsRequest(String country, String type, int limit) {
        this.country = country;
        this.type = type;
        this.limit = limit;
    }

    public String getCountry() {
        return country;
    }

    public String getType() {
        return type;
    }

    public int getLimit() {
        return limit;
    }
}
