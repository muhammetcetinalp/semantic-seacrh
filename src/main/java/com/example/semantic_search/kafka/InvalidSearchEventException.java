package com.example.semantic_search.kafka;

/** A permanent input error, sent to the dead-letter topic without retries. */
public class InvalidSearchEventException extends RuntimeException {
    public InvalidSearchEventException(String message) { super(message); }
    public InvalidSearchEventException(String message, Throwable cause) { super(message, cause); }
}
