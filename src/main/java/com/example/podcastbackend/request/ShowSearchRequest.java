package com.example.podcastbackend.request;

import java.util.List;

public class ShowSearchRequest {

    private String q;
    private Integer page = 1;
    private Integer size = 10;
    private List<String> language;

    public String getQ() {
        return q;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }

    public List<String> getLanguage() {
        return language;
    }
}