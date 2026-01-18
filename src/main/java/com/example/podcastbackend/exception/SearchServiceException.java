package com.example.podcastbackend.exception;

public class SearchServiceException extends RuntimeException {
    public SearchServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
