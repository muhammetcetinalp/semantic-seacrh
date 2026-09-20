package com.example.semantic_search.exception;

public class OpenSearchUnavailableException extends SearchServiceException {

    public OpenSearchUnavailableException(String message) {
        super(message);
    }

    public OpenSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
