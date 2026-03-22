package com.example.podcastbackend.exception;

public class CrossIndexPageLimitException extends RuntimeException {

    public CrossIndexPageLimitException(String message) {
        super(message);
    }
}
