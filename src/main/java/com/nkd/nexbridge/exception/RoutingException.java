package com.nkd.nexbridge.exception;

public class RoutingException extends NexBridgeException {
    public RoutingException(String message) {
        super("ROUTING_ERROR", message);
    }

    public RoutingException(String message, Throwable cause) {
        super("ROUTING_ERROR", message, cause);
    }
}
