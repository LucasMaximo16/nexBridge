package com.nkd.nexbridge.exception;

public class ConnectorException extends NexBridgeException {
    public ConnectorException(String message) {
        super("CONNECTOR_ERROR", message);
    }

    public ConnectorException(String message, Throwable cause) {
        super("CONNECTOR_ERROR", message, cause);
    }

    public ConnectorException(String errorCode, String message) {
        super(errorCode, message);
    }
}
