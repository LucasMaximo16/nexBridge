package com.nkd.nexbridge.exception;

public class NexBridgeException extends RuntimeException {
    private final String errorCode;

    public NexBridgeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NexBridgeException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
