package com.nkd.nexbridge.exception;

import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import com.nkd.nexbridge.config.NexBridgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final NexBridgeProperties properties;

    @ExceptionHandler(ConnectorException.class)
    public ResponseEntity<NexResponse<Void>> handleConnectorException(ConnectorException ex) {
        log.error("ConnectorException: code={} message={}", ex.getErrorCode(), ex.getMessage());
        var error = NexError.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .httpStatus(HttpStatus.BAD_GATEWAY.value())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(NexResponse.error(error, buildMeta()));
    }

    @ExceptionHandler(NexBridgeException.class)
    public ResponseEntity<NexResponse<Void>> handleNexBridgeException(NexBridgeException ex) {
        log.error("NexBridgeException: code={} message={}", ex.getErrorCode(), ex.getMessage());
        var error = NexError.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(NexResponse.error(error, buildMeta()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<NexResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        var error = NexError.builder()
                .code("VALIDATION_ERROR")
                .message(message)
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .build();
        return ResponseEntity.badRequest()
                .body(NexResponse.error(error, buildMeta()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<NexResponse<Void>> handleAuth(AuthenticationException ex) {
        var error = NexError.builder()
                .code("UNAUTHORIZED")
                .message("Autenticação necessária")
                .httpStatus(HttpStatus.UNAUTHORIZED.value())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(NexResponse.error(error, buildMeta()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<NexResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        var error = NexError.builder()
                .code("FORBIDDEN")
                .message("Acesso negado")
                .httpStatus(HttpStatus.FORBIDDEN.value())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(NexResponse.error(error, buildMeta()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<NexResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        var error = NexError.builder()
                .code("INTERNAL_ERROR")
                .message("Erro interno do servidor")
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(NexResponse.error(error, buildMeta()));
    }

    private NexMeta buildMeta() {
        return NexMeta.builder()
                .timestamp(Instant.now().toString())
                .nexbridgeVersion(properties.getVersion())
                .build();
    }
}
