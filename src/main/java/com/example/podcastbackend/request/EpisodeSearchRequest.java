package com.example.podcastbackend.request;

import java.util.List;

public class EpisodeSearchRequest {

    private String q;
    private int page = 1;
    private int size = 20;
    private String sort; // "relevance" | "date"
    private List<String> language;

    public String getQ() { return q; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public String getSort() { return sort; }
    public List<String> getLanguage() { return language; }

    public boolean sortByDate() {
        return "date".equalsIgnoreCase(sort);
    }

    public int from() {
        return (page - 1) * size;
    }
}