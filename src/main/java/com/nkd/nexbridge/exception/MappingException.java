package com.nkd.nexbridge.exception;

public class MappingException extends NexBridgeException {
    public MappingException(String message) {
        super("MAPPING_ERROR", message);
    }

    public MappingException(String message, Throwable cause) {
        super("MAPPING_ERROR", message, cause);
    }

    public MappingException(String errorCode, String message) {
        super(errorCode, message);
    }
}
