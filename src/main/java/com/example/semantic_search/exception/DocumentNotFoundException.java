package com.example.semantic_search.exception;

public class DocumentNotFoundException extends SearchServiceException {

    public DocumentNotFoundException(String documentId) {
        super("Document not found: " + documentId);
    }

    public DocumentNotFoundException(String documentId, String indexName) {
        super("Document not found: " + documentId + " in index: " + indexName);
    }
}
