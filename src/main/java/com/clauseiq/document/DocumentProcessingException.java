package com.clauseiq.document;

/** A document problem whose message is safe to show to the user (unlike raw parser/provider errors). */
public class DocumentProcessingException extends RuntimeException {

    public DocumentProcessingException(String userMessage) {
        super(userMessage);
    }
}
