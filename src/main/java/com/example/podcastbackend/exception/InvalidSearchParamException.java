package com.example.podcastbackend.exception;

public class InvalidSearchParamException extends RuntimeException {

    public InvalidSearchParamException(String message) {
        super(message);
    }
}
