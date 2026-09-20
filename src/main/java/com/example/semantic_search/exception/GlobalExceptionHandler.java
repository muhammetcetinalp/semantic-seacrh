package com.example.semantic_search.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Global exception handler — keeps controllers clean of try/catch blocks
 * and ensures clients never receive stack traces or internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return handleExceptionInternal(ex,
                new ErrorResponse(400, "Validation Error", message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ErrorResponse) {
            return super.handleExceptionInternal(ex, body, headers, status, request);
        }

        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String error = httpStatus != null ? httpStatus.getReasonPhrase() : "Request Error";
        String message = switch (status.value()) {
            case 400 -> "Request is malformed or contains invalid values.";
            case 404 -> "Requested resource was not found.";
            case 405 -> "HTTP method is not supported for this resource.";
            case 406 -> "Requested response format is not supported.";
            case 415 -> "Request content type is not supported.";
            default -> status.is5xxServerError()
                    ? "An unexpected error occurred. Please try again later."
                    : "Request could not be processed.";
        };
        return super.handleExceptionInternal(ex,
                new ErrorResponse(status.value(), error, message), headers, status, request);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(OpenSearchUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleOpenSearchUnavailable(OpenSearchUnavailableException ex) {
        log.error("OpenSearch unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "Service Unavailable",
                        "Search engine is currently unavailable. Please try again later."));
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEmbeddingUnavailable(EmbeddingUnavailableException ex) {
        log.error("Embedding service unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "Service Unavailable",
                        "Embedding service is currently unavailable. Please try again later."));
    }

    @ExceptionHandler(SearchServiceException.class)
    public ResponseEntity<ErrorResponse> handleSearchService(SearchServiceException ex) {
        log.error("Search service error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Error",
                        "An unexpected error occurred. Please try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Error",
                        "An unexpected error occurred. Please try again later."));
    }
}
