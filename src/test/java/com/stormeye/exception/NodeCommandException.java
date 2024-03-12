package com.stormeye.exception;

public class NodeCommandException extends RuntimeException {

    public NodeCommandException(final Throwable cause) {
        super(cause);
    }

    public NodeCommandException(final String message) {
        super("Node command failed with: " + message);
    }
}
